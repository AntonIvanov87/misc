import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class JStackSorter {

  public static void main(String[] args) throws IOException {
    try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in))) {
      proxyHeaders(bufferedReader);
      List<List<String>> stacks = readStacks(bufferedReader);
      stacks.sort(Comparator.comparing(stack -> stack.get(0)));
      printStacks(stacks);
    }
  }

  private static void proxyHeaders(BufferedReader bufferedReader) throws IOException {
    for (int n=1; n<=3; n++) {
      String line = bufferedReader.readLine();
      if (line == null) {
        throw new IllegalStateException("Unexpected end of jstack header on line " + n);
      }
      System.out.println(line);
    }
  }

  private static List<List<String>> readStacks(BufferedReader bufferedReader) throws IOException {
    List<List<String>> stacks = new ArrayList<>();
    List<String> currentStack = new ArrayList<>();
    while (true) {
      String line = bufferedReader.readLine();
      if (line == null) {
        break;
      } else if (line.trim().isEmpty()) {
        stacks.add(currentStack);
        currentStack = new ArrayList<>();
      } else {
        currentStack.add(line);
      }
    }
    return stacks;
  }

  private static void printStacks(List<List<String>> stacks) {
    for (List<String> stack : stacks) {
      for (String line : stack) {
        System.out.println(line);
      }
      System.out.println();
    }
  }
}
