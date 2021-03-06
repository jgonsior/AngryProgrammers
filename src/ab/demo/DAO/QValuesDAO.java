package ab.demo.DAO;

import ab.demo.other.Action;
import ab.demo.other.GameState;
import ab.vision.ABObject;
import ab.vision.ABType;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import java.awt.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * @author jgonsior
 */
@RegisterMapper(QValuesDAO.ActionMapper.class)
public interface QValuesDAO {

    @SqlUpdate("CREATE TABLE IF NOT EXISTS q_values " +
            "(q_value DOUBLE PRECISION, stateId INT, x INT, y INT, targetObjectType VARCHAR(22), aboveCount INT, leftCount INT, rightCount INT, belowCount INT, distanceToPig DOUBLE PRECISION, trajectoryType VARCHAR(4), targetObject VARCHAR(150), pigsLeft INT," +
            " PRIMARY KEY(stateId, x, y, targetObjectType, aboveCount, leftCount, rightCount, belowCount, distanceToPig, trajectoryType, pigsLeft))")
    void createTable();

    @SqlUpdate("UPDATE q_values SET q_value=:q_value " +
            "WHERE stateId=:stateId AND x=:x AND y=:y AND targetObjectType=:targetObjectType AND aboveCount=:aboveCount " +
            "AND leftCount=:leftCount AND rightCount=:rightCount AND belowCount=:belowCount AND distanceToPig=:distanceToPig AND trajectorytype=:trajectoryType AND pigsLeft=:pigsLeft;")
    void updateQValue(
            @Bind("q_value") double qValue,
            @Bind("stateId") int stateId,
            @Bind("x") int x,
            @Bind("y") int y,
            @Bind("targetObjectType") String targetObjectType,
            @Bind("aboveCount") int aboveCount,
            @Bind("leftCount") int leftCount,
            @Bind("rightCount") int rightCount,
            @Bind("belowCount") int belowCount,
            @Bind("distanceToPig") double distanceToPig,
            @Bind("trajectoryType") String trajectoryType,
            @Bind("pigsLeft") int pigsLeft
    );

    @SqlUpdate("INSERT INTO q_values(" +
            "q_value, stateId, x, y, targetObjectType, aboveCount, leftCount, rightCount, belowCount, distanceToPig, trajectoryType, targetObject, pigsLeft" +
            ") VALUES (" +
            ":q_value, :stateId, :x, :y, :targetObjectType, :aboveCount, :leftCount, :rightCount, :belowCount, :distanceToPig, :trajectoryType, :targetObject, :pigsLeft" +
            ")")
    void insertNewAction(
            @Bind("q_value") double qValue,
            @Bind("stateId") int stateId,
            @Bind("x") int x,
            @Bind("y") int y,
            @Bind("targetObjectType") String targetObjectType,
            @Bind("aboveCount") int aboveCount,
            @Bind("leftCount") int leftCount,
            @Bind("rightCount") int rightCount,
            @Bind("belowCount") int belowCount,
            @Bind("distanceToPig") double distanceToPig,
            @Bind("trajectoryType") String trajectoryType,
            @Bind("targetObject") String targetObject,
            @Bind("pigsLeft") int pigsLeft
    );

    @SqlQuery("SELECT q_value FROM q_values WHERE stateId=:stateId AND x=:x AND y=:y AND targetObjectType=:targetObjectType AND aboveCount=:aboveCount " +
            "AND leftCount=:leftCount AND rightCount=:rightCount AND belowCount=:belowCount AND distanceToPig=:distanceToPig AND trajectorytype=:trajectoryType AND pigsLeft=:pigsLeft;")
    double getQValue(
            @Bind("stateId") int stateId,
            @Bind("x") int x,
            @Bind("y") int y,
            @Bind("targetObjectType") String targetObjectType,
            @Bind("aboveCount") int aboveCount,
            @Bind("leftCount") int leftCount,
            @Bind("rightCount") int rightCount,
            @Bind("belowCount") int belowCount,
            @Bind("distanceToPig") double distanceToPig,
            @Bind("trajectoryType") String trajectoryType,
            @Bind("pigsLeft") int pigsLeft
    );

    @SqlQuery("SELECT MAX(q_value) FROM q_values WHERE stateId=:stateId;")
    double getHighestQValue(@Bind("stateId") int stateId);

    @SqlQuery("SELECT stateId, x, y, targetObjectType, aboveCount, leftCount, rightCount, belowCount, distanceToPig, trajectoryType, targetObject, pigsLeft FROM q_values WHERE stateId=:stateId ORDER BY q_value DESC LIMIT 1;")
    Action getBestAction(@Bind("stateId") int stateId);

    @SqlQuery("SELECT stateId, x, y, targetObjectType, aboveCount, leftCount, rightCount, belowCount, distanceToPig,  trajectoryType, targetObject, pigsLeft FROM q_values WHERE stateId=:stateId ORDER BY RANDOM() LIMIT 1;")
    Action getRandomAction(@Bind("stateId") int stateId);

    @SqlQuery("SELECT COUNT(*) FROM q_values WHERE stateId=:stateId GROUP BY x, y, targetObjectType, aboveCount, leftCount, rightCount, belowCount, distanceToPig,  trajectoryType, pigsLeft;")
    int getActionCount(@Bind("stateId") int stateId);

    @SqlQuery("SELECT count(stateId) FROM q_values WHERE x=:x AND y=:y AND targetObjectType=:targetObjectType AND aboveCount=:aboveCount " +
            "AND leftCount=:leftCount AND rightCount=:rightCount AND belowCount=:belowCount AND distanceToPig=:distanceToPig AND trajectorytype=:trajectoryType AND pigsLeft=:pigsLeft")
    int countStateIds(@Bind("x") int x,
                      @Bind("y") int y,
                      @Bind("targetObjectType") String targetObjectType,
                      @Bind("aboveCount") int aboveCount,
                      @Bind("leftCount") int leftCount,
                      @Bind("rightCount") int rightCount,
                      @Bind("belowCount") int belowCount,
                      @Bind("distanceToPig") double distanceToPig,
                      @Bind("trajectoryType") String trajectoryType);

    @SqlQuery("SELECT stateId FROM q_values " +
            "WHERE :x BETWEEN x-5 AND x+5 AND :y BETWEEN y-5 AND y+5 " +
            "AND targetObjectType=:targetObjectType " +
            "AND :aboveCount BETWEEN aboveCount-2 AND aboveCount+2" +
            "AND :leftCount BETWEEN leftCount-2 AND leftCount+2 " +
            "AND :rightCount BETWEEN rightCount-2 AND rightCount+2 " +
            "AND :belowCount BETWEEN belowCount-2 AND belowCount+2 " +
            "AND :distanceToPig BETWEEN distanceToPig-3 AND distanceToPig+3 " +
            "AND trajectorytype=:trajectoryType "+
            "AND pigsLeft =:pigsLeft")
    List<Integer> getStateIds(@Bind("x") int x,
                              @Bind("y") int y,
                              @Bind("targetObjectType") String targetObjectType,
                              @Bind("aboveCount") int aboveCount,
                              @Bind("leftCount") int leftCount,
                              @Bind("rightCount") int rightCount,
                              @Bind("belowCount") int belowCount,
                              @Bind("distanceToPig") double distanceToPig,
                              @Bind("trajectoryType") String trajectoryType,
                              @Bind("pigsLeft") int pigsLeft);

    /**
     * closes the connection
     */
    void close();


    class ActionMapper implements ResultSetMapper<Action> {
        public Action map(int index, ResultSet resultSet, StatementContext ctx) throws SQLException {
            List<Action> possibleActions = GameState.getProblemState().getPossibleActions();

            //try to find action
            for (Action possibleAction : possibleActions) {
                if (
                        possibleAction.getTargetObject().type == ABType.valueOf(resultSet.getString("targetObjectType")) &&
                            Math.abs(possibleAction.getTargetObject().objectsAboveCount - resultSet.getInt("aboveCount")) < 3 &&
                            Math.abs(possibleAction.getTargetObject().objectsRightCount - resultSet.getInt("rightCount")) < 3 &&
                            Math.abs(possibleAction.getTargetObject().objectsLeftCount - resultSet.getInt("leftCount")) < 3 &&
                            Math.abs(possibleAction.getTargetObject().objectsBelowCount - resultSet.getInt("belowCount")) < 3 &&
                            Math.abs(possibleAction.getTargetObject().distanceToPigs - resultSet.getDouble("distanceToPig")) < 6 &&
                            Math.abs(possibleAction.getTargetObject().x - resultSet.getInt("x")) < 6 &&
                            Math.abs(possibleAction.getTargetObject().y - resultSet.getInt("y")) < 6 &&
                            possibleAction.pigsLeft == resultSet.getInt("pigsLeft") &&
                            possibleAction.getTrajectoryType() == ABObject.TrajectoryType.valueOf(resultSet.getString("trajectoryType"))
                        ) {
                    return possibleAction;
                }
            }
            // we allow 1 wrong action so we should instantiate it here ourself
            ABObject targetObject = new ABObject(
                    new Rectangle(new Point(resultSet.getInt("x"), resultSet.getInt("y"))),
                    ABType.valueOf(resultSet.getString("targetObjectType"))
            );

            targetObject.setObjectsAround(resultSet.getInt("aboveCount"), resultSet.getInt("leftCount"), resultSet.getInt("rightCount"), resultSet.getInt("belowCount"), resultSet.getDouble("distanceToPig"));
            return new Action(targetObject, ABObject.TrajectoryType.valueOf(resultSet.getString("trajectoryType")), resultSet.getInt("pigsLeft"));


            //throw new InstantiationError("Couldn't find the requested target object in the actions for this problemstate");
        }
    }
}