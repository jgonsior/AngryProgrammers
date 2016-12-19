package ab.demo.qlearning;

public class StateObject {
    public int stateId;
    public String objectIds;

    public StateObject(int stateId, String objectIds) {
      this.stateId = stateId;
      this.objectIds = objectIds;
   }

   public String toString(){
   	return String.valueOf(stateId) + " " + String.valueOf(objectIds);
   }
}