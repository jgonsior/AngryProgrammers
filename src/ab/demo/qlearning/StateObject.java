package ab.demo.qlearning;

import java.util.HashSet;
import java.util.Set;

/**
 * removes some integers from some ids?!
 * <p>
 * why isn't it part of ProblemState?!
 */
public class StateObject {
    public int stateId;
    public Set<Integer> objectIds;

    public StateObject(int stateId, String objectIds) {
        this.objectIds = new HashSet<Integer>();
        this.stateId = stateId;
        String[] parts = objectIds.split(",");
        for (String part : parts) {
            this.objectIds.add(Integer.valueOf(part.replaceAll("[^\\d.]", "")));
        }
    }
}