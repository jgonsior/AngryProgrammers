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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * @author jgonsior
 */
@RegisterMapper(QValuesDAO.ActionMapper.class)
public interface QValuesDAO {

    @SqlUpdate("CREATE TABLE IF NOT EXISTS q_values " +
            "(q_value DOUBLE PRECISION, stateId INT, x INT, y INT, targetObjectType VARCHAR(22), aboveCount INT, leftCount INT, rightCount INT, belowCount INT, distanceToPig DOUBLE PRECISION, trajectoryType VARCHAR(4), targetObject VARCHAR(150)," +
            " PRIMARY KEY(stateId, x, y, targetObjectType, aboveCount, leftCount, rightCount, belowCount, distanceToPig, trajectoryType))")
    void createTable();

    @SqlUpdate("UPDATE q_values SET q_value=:q_value " +
            "WHERE stateId=:stateId AND x=:x AND y=:y AND targetObjectType=:targetObjectType AND aboveCount=:aboveCount " +
            "AND leftCount=:leftCount AND rightCount=:rightCount AND belowCount=:belowCount AND distanceToPig=:distanceToPig AND trajectorytype=:trajectoryType;")
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
            @Bind("trajectoryType") String trajectoryType
    );

    @SqlUpdate("INSERT INTO q_values(" +
            "q_value, stateId, x, y, targetObjectType, aboveCount, leftCount, rightCount, belowCount, distanceToPig, trajectoryType, targetObject" +
            ") VALUES (" +
            ":q_value, :stateId, :x, :y, :targetObjectType, :aboveCount, :leftCount, :rightCount, :belowCount, :distanceToPig, :trajectoryType, :targetObject" +
            ");")
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
            @Bind("targetObject") String targetObject
    );

    @SqlQuery("SELECT q_value FROM q_values WHERE stateId=:stateId AND x=:x AND y=:y AND targetObjectType=:targetObjectType AND aboveCount=:aboveCount " +
            "AND leftCount=:leftCount AND rightCount=:rightCount AND belowCount=:belowCount AND distanceToPig=:distanceToPig AND trajectorytype=:trajectoryType;")
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
            @Bind("trajectoryType") String trajectoryType
    );

    @SqlQuery("SELECT MAX(q_value) FROM q_values WHERE stateId=:stateId;")
    double getHighestQValue(@Bind("stateId") int stateId);

    @SqlQuery("SELECT stateId, x, y, targetObjectType, aboveCount, leftCount, rightCount, belowCount, distanceToPig, trajectoryType, targetObject FROM q_values WHERE stateId=:stateId ORDER BY q_value DESC LIMIT 1;")
    Action getBestAction(@Bind("stateId") int stateId);

    @SqlQuery("SELECT stateId, x, y, targetObjectType, aboveCount, leftCount, rightCount, belowCount, distanceToPig,  trajectoryType, targetObject FROM q_values WHERE stateId=:stateId ORDER BY RANDOM() LIMIT 1;")
    Action getRandomAction(@Bind("stateId") int stateId);

    @SqlQuery("SELECT COUNT(*) FROM q_values WHERE stateId=:stateId GROUP BY x, y, targetObjectType, aboveCount, leftCount, rightCount, belowCount, distanceToPig,  trajectoryType;")
    int getActionCount(@Bind("stateId") int stateId);

    @SqlQuery("SELECT stateId FROM q_values WHERE x=:x AND y=:y AND targetObjectType=:targetObjectType AND aboveCount=:aboveCount " +
            "AND leftCount=:leftCount AND rightCount=:rightCount AND belowCount=:belowCount AND distanceToPig=:distanceToPig AND trajectorytype=:trajectoryType")
    int getStateId(@Bind("x") int x,
                   @Bind("y") int y,
                   @Bind("targetObjectType") String targetObjectType,
                   @Bind("aboveCount") int aboveCount,
                   @Bind("leftCount") int leftCount,
                   @Bind("rightCount") int rightCount,
                   @Bind("belowCount") int belowCount,
                   @Bind("distanceToPig") double distanceToPig,
                   @Bind("trajectoryType") String trajectoryType);

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
                                possibleAction.getTargetObject().objectsAboveCount == resultSet.getInt("aboveCount") &&
                                possibleAction.getTargetObject().objectsRightCount == resultSet.getInt("rightCount") &&
                                possibleAction.getTargetObject().objectsLeftCount == resultSet.getInt("leftCount") &&
                                possibleAction.getTargetObject().objectsBelowCount == resultSet.getInt("belowCount") &&
                                possibleAction.getTargetObject().distanceToPigs == resultSet.getDouble("distanceToPig") &&
                                ((possibleAction.getTargetObject().x + 9) / 10) * 10 == ((resultSet.getInt("x") + 9) / 10) * 10 &&
                                ((possibleAction.getTargetObject().y + 9) / 10) * 10 == ((resultSet.getInt("y") + 9) / 10) * 10 &&
                                possibleAction.getTrajectoryType() == ABObject.TrajectoryType.valueOf(resultSet.getString("trajectoryType"))
                        ) {
                    return possibleAction;
                }
            }

            throw new InstantiationError("Couldn't find the requested target object in the actions for this problemstate");
        }
    }
}