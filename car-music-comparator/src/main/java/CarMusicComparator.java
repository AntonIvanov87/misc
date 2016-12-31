import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;

import static java.nio.file.Files.isDirectory;
import static java.util.Comparator.comparing;

public class CarMusicComparator {

  public static void main(String[] args) throws IOException {
    compare(
        Paths.get("/", "home", "an-ivanov", "yandex-disk", "music"),
        Paths.get("/", "home", "an-ivanov", "car-music")
    );
  }

  private static void compare(Path path1, Path path2) throws IOException {
    Iterator<Path> iterator1 = sortedContents(path1);
    Iterator<Path> iterator2 = sortedContents(path2);
    Path subPath1 = iterator1.next();
    Path subPath2 = iterator2.next();
    while (true) {
      String name1 = getNormalizedName(subPath1);
      String name2 = getNormalizedName(subPath2);
      int name1ToName2 = name1.compareTo(name2);
      if (name1ToName2 == 0) {
        boolean isDirectory1 = isDirectory(subPath1);
        boolean isDirectory2 = isDirectory(subPath2);
        if (isDirectory1 && !isDirectory2) {
          throw new RuntimeException(subPath1 + " is a directory, but " + subPath2 + " is not");
        }
        if (!isDirectory1 && isDirectory2) {
          throw new RuntimeException(subPath1 + " is not a directory, but " + subPath2 + " is");
        }

        if (isDirectory1 && isDirectory2) {
          compare(subPath1, subPath2);
        }

        if (!iterator1.hasNext() && !iterator2.hasNext()) {
          break;
        }
        if (!iterator1.hasNext()) {
          reachedEnd(path1, iterator2);
          break;
        }
        if (!iterator2.hasNext()) {
          reachedEnd(path2, iterator1);
          break;
        }
        subPath1 = iterator1.next();
        subPath2 = iterator2.next();

      } else if (name1ToName2 < 0) {
        if (!isDirectory(subPath1)) {
          System.out.println("'" + path2 + "' does not have '" + name1 + "'");
        }

        if (!iterator1.hasNext() && !iterator2.hasNext()) {
          break;
        }
        if (iterator1.hasNext()) {
          subPath1 = iterator1.next();
        } else {
          reachedEnd(path1, iterator2);
          break;
        }

      } else { // name1 > name2
        System.out.println("'" + path1 + "' does not have '" + name2 + "'");

        if (!iterator1.hasNext() && !iterator2.hasNext()) {
          break;
        }
        if (iterator2.hasNext()) {
          subPath2 = iterator2.next();
        } else {
          reachedEnd(path2, iterator1);
          break;
        }
      }

    }
  }

  private static Iterator<Path> sortedContents(Path path) throws IOException {
    return Files.list(path).sorted(comparing(Path::toString)).iterator();
  }

  private static String getNormalizedName(Path path) {
    String name = path.getName(path.getNameCount() - 1).toString();
    if (Files.isRegularFile(path)) {
      int indexOfExtension = name.lastIndexOf('.');
      if (indexOfExtension == -1) {
        throw new RuntimeException(path + " does not have extension");
      }
      return name.substring(0, indexOfExtension);
    } else {
      return name;
    }
  }

  private static void reachedEnd(Path path, Iterator<Path> otherSubPaths) {
    while (otherSubPaths.hasNext()) {
      Path subPath = otherSubPaths.next();
      String name = getNormalizedName(subPath);
      System.out.println("'" + path + "' does not have '" + name + "'");
    }
  }

}
