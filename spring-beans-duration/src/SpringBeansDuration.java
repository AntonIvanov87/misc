import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;

public class SpringBeansDuration {

  public static void main(String[] args) throws IOException, JAXBException {
    if (args.length < 1) {
      System.err.println("Missing file name");
      return;
    }
    String fileName = args[0];

    List<Bean> beans = parseBeansFromFile(fileName);

    fillTimeSelf(beans);

    String fileNameWOExtension = fileName.substring(0, fileName.lastIndexOf('.'));

    String flatFileName = fileNameWOExtension + "-flat.txt";
    toFlatFile(beans, flatFileName);

    String treeFileName = fileNameWOExtension + "-tree.xml";
    toTreeFile(beans, treeFileName);
  }

  static class Bean {
    long timeSelfNs;
    long timeWithSubBeansNs;
    String name;
    String type;
    List<Bean> subBeans = new ArrayList<>();
  }

  static List<Bean> parseBeansFromFile(String fileName) throws IOException {
    Path filePath = Paths.get(fileName);
    List<Bean> beans;
    try (BufferedReader bufferedReader = Files.newBufferedReader(filePath)) {
      beans = parseBeans(bufferedReader);
    }
    return beans;
  }

  static List<Bean> parseBeans(BufferedReader bufferedReader) throws IOException {
    List<Bean> beans = new ArrayList<>();
    while (true) {
      String line = bufferedReader.readLine();
      if (line == null) {
        return beans;
      }
      if (line.startsWith("Start bean ")) {
        Bean bean = parseStartBean(line);
        parseSubBeansAndFinishBean(bean, bufferedReader);
        beans.add(bean);
      }
    }
  }

  static Bean parseStartBean(String startLine) {
    String[] parts = startLine.split(" ");
    Bean bean = new Bean();
    bean.name = parts[2];
    if (!parts[4].equals("null")) {
      bean.type = parts[4];
      if (parts.length >= 6) {
        bean.type += ' ' + parts[5];
      }
    }
    return bean;
  }

  static void parseSubBeansAndFinishBean(Bean bean, BufferedReader bufferedReader) throws IOException {
    while (true) {
      String line = bufferedReader.readLine();
      if (line.startsWith("Start bean ")) {
        Bean subBean = parseStartBean(line);
        parseSubBeansAndFinishBean(subBean, bufferedReader);
        bean.subBeans.add(subBean);
      } else if (line.startsWith("Finish bean ")) {
        bean.timeWithSubBeansNs = parseTime(line);
        return;
      }
    }
  }

  static long parseTime(String line) {
    String[] parts = line.split(" ");
    String timeStr = parts[parts.length-2];
    return Long.parseLong(timeStr);
  }

  static void fillTimeSelf(List<Bean> beans) {
    for (Bean bean : beans) {
      fillTimeSelf(bean);
    }
  }

  static void fillTimeSelf(Bean bean) {
    long timeSubBeansNs = 0;
    for (Bean subBean : bean.subBeans) {
      timeSubBeansNs += subBean.timeWithSubBeansNs;
    }
    bean.timeSelfNs = bean.timeWithSubBeansNs - timeSubBeansNs;

    fillTimeSelf(bean.subBeans);
  }

  static void toFlatFile(List<Bean> beans, String fileName) throws IOException {
    List<GroupedBean> flatBeans = flattenBeans(beans);
    List<GroupedBean> groupedBeans = groupBeans(flatBeans);
    groupedBeans.sort(Comparator.<GroupedBean>comparingLong(bean -> bean.cumTimeSelfNs).reversed());
    groupedBeans.forEach(bean -> bean.timesSelfNs.sort(Comparator.<Long>reverseOrder()));
    groupedBeansToFlatFile(groupedBeans, fileName);
  }

  static List<GroupedBean> flattenBeans(List<Bean> beans) {
    List<GroupedBean> flatBeans = new ArrayList<>();
    for (Bean bean : beans) {
      flatBeans.add(toGrouped(bean));
      flatBeans.addAll(flattenBeans(bean.subBeans));
    }
    return flatBeans;
  }

  static class GroupedBean {
    String name;
    String type;
    long cumTimeSelfNs;
    List<Long> timesSelfNs = new ArrayList<>();
  }

  static GroupedBean toGrouped(Bean bean) {
    GroupedBean groupedBean = new GroupedBean();
    groupedBean.name = bean.name;
    groupedBean.type = bean.type;
    groupedBean.cumTimeSelfNs = bean.timeSelfNs;
    groupedBean.timesSelfNs.add(bean.timeSelfNs);
    return groupedBean;
  }

  static List<GroupedBean> groupBeans(List<GroupedBean> flatBeans) {
    Map<String, GroupedBean> nameToBean = new HashMap<>();
    for (GroupedBean bean : flatBeans) {
      GroupedBean groupedBean = nameToBean.get(bean.name);
      if (groupedBean == null) {
        nameToBean.put(bean.name, bean);
      } else {
        groupedBean.cumTimeSelfNs += bean.cumTimeSelfNs;
        groupedBean.timesSelfNs.add(bean.cumTimeSelfNs);
      }
    }
    List<GroupedBean> groupedBeans = nameToBean.values().stream().collect(toList());
    return groupedBeans;
  }

  private static void groupedBeansToFlatFile(List<GroupedBean> beans, String fileName) throws IOException {
    long cumTimeSelfNs = beans.stream().mapToLong(groupedBean -> groupedBean.cumTimeSelfNs).sum();
    try (PrintWriter printWriter = new PrintWriter(fileName)) {
      printWriter.println(toMs(cumTimeSelfNs) + " ms all");
      for (GroupedBean bean : beans) {
        List<Integer> topTimesSelfMs = bean.timesSelfNs.stream().map(SpringBeansDuration::toMs).limit(4).collect(toList());
        printWriter.println(toMs(bean.cumTimeSelfNs) + " ms " + bean.timesSelfNs.size() + " calls " + topTimesSelfMs + " " + bean.name + " type " + bean.type);
        cumTimeSelfNs += bean.cumTimeSelfNs;
      }
    }
  }

  static void toTreeFile(List<Bean> beans, String fileName) throws JAXBException {
    XmlBeans rootElem = new XmlBeans();
    rootElem.beans = beans.stream().map(SpringBeansDuration::toXml).collect(toList());

    sort(rootElem.beans);

    JAXBContext jaxbContext = JAXBContext.newInstance(XmlBeans.class, Bean.class);
    Marshaller marshaller = jaxbContext.createMarshaller();
    marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
    marshaller.marshal(rootElem, new File(fileName));
  }

  static void sort(List<XmlBean> beans) {
    beans.sort(xmlBeanComparator);
    beans.forEach(bean -> sort(bean.subBeans));
  }

  static final Comparator<XmlBean> xmlBeanComparator = Comparator.<XmlBean>comparingInt(bean -> bean.timeWithSubBeansMs).reversed();

  static XmlBean toXml(Bean bean) {
    XmlBean xmlBean = new XmlBean();
    xmlBean.timeSelfMs = toMs(bean.timeSelfNs);
    xmlBean.timeWithSubBeansMs = toMs(bean.timeWithSubBeansNs);
    xmlBean.name = bean.name;
    xmlBean.type = bean.type;
    xmlBean.subBeans = bean.subBeans.stream().map(SpringBeansDuration::toXml).collect(toList());
    return xmlBean;
  }

  @XmlRootElement(name="beans")
  static class XmlBeans {
    @XmlElement(name = "bean")
    List<XmlBean> beans;
  }

  static class XmlBean {
    @XmlAttribute
    int timeSelfMs;
    @XmlAttribute
    int timeWithSubBeansMs;
    @XmlAttribute
    String name;
    @XmlAttribute
    String type;
    @XmlElement(name = "bean")
    List<XmlBean> subBeans = new ArrayList<>();
  }

  static int toMs(long ns) {
    return (int) (ns / (1000 * 1000));
  }
}
