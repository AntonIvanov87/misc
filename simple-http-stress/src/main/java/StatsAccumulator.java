import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

// TODO: reuse with NettyHttpStress
class StatsAccumulator {

  private final Map<String, LongAdder> resultToCount = new ConcurrentHashMap<>();

  void addResult(String result) {
    resultToCount.computeIfAbsent(result, k -> new LongAdder()).increment();
  }

  void printStats(float secElapsed) {
    List<Map.Entry<String, Integer>> snapshot = getSnapshotAndReset();
    snapshot.removeIf(entry -> entry.getValue().equals(0));

    float rps = snapshot.stream().mapToInt(Map.Entry::getValue).sum() / secElapsed;

    String snapshotStr = snapshot.stream()
                             .sorted(Comparator.comparing(Map.Entry::getKey))
                             .map(entry -> "" + (entry.getValue() / secElapsed) + " '" + entry.getKey() + "'/sec")
                             .reduce("", (s1, s2) -> s1 + ", " + s2)
                             .substring(2);

    System.out.println(rps + " rps, " + snapshotStr);
  }

  private List<Map.Entry<String, Integer>> getSnapshotAndReset() {
    List<Map.Entry<String, Integer>> result = new ArrayList<>(resultToCount.size());
    resultToCount.forEach((k, v) -> {
      int sum = (int) v.sumThenReset();
      result.add(new AbstractMap.SimpleImmutableEntry<>(k, sum));
    });
    return result;
  }
}
