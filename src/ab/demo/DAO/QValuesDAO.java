package ab.demo.DAO;

import ab.demo.other.Action;
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

/**
 * @author jgonsior
 */
@RegisterMapper(QValuesDAO.ActionMapper.class)
public interface QValuesDAO {

    @SqlUpdate("CREATE TABLE IF NOT EXISTS q_values " +
            "(q_value DOUBLE PRECISION, stateId INT, targetObjectType VARCHAR(22), aboveCount INT, leftCount INT, rightCount INT, belowCount INT, distanceToPig DOUBLE PRECISION, trajectoryType VARCHAR(4), targetObject VARCHAR(150)," +
            " PRIMARY KEY(stateId, targetObjectType, aboveCount, leftCount, rightCount, belowCount, distanceToPig, trajectoryType))")
    void createTable();

    @SqlUpdate("UPDATE q_values SET q_value=:q_value " +
            "WHERE stateId=:stateId AND targetObjectType=:targetObjectType AND aboveCount=:aboveCount " +
            "AND leftCount=:leftCount AND rightCound=:rightCount AND belowCount=:belowCount AND distanceToPig=:distanceToPig AND trajectorytype=:trajectoryType;")
    void updateQValue(
            @Bind("q_value") double qValue,
            @Bind("stateId") int stateId,
            @Bind("targetObjectType") String targetObjectType,
            @Bind("aboveCount") int aboveCount,
            @Bind("leftCount") int leftCount,
            @Bind("rightCount") int rightCount,
            @Bind("belowCount") int belowCount,
            @Bind("distanceToPig") double distanceToPig,
            @Bind("trajectoryType") String trajectoryType
    );

    @SqlUpdate("INSERT INTO q_values(" +
            "q_value, stateId, targetObjectType, aboveCount, leftCount, rightCount, belowCount, distanceToPig, trajectoryType, targetObject" +
            ") VALUES (" +
            ":q_value, :stateId, :targetObjectType, :aboveCount, :leftCount, :rightCount, :belowCount, :distanceToPig, :trajectoryType, :targetObject" +
            ");")
    void insertNewAction(
            @Bind("q_value") double qValue,
            @Bind("stateId") int stateId,
            @Bind("targetObjectType") String targetObjectType,
            @Bind("aboveCount") int aboveCount,
            @Bind("leftCount") int leftCount,
            @Bind("rightCount") int rightCount,
            @Bind("belowCount") int belowCount,
            @Bind("distanceToPig") double distanceToPig,
            @Bind("trajectoryType") String trajectoryType,
            @Bind("targetObject") String targetObject
    );

    @SqlQuery("SELECT q_value FROM q_values WHERE stateId=:stateId AND targetObjectType=:targetObjectType AND aboveCount=:aboveCount " +
            "AND leftCount=:leftCount AND rightCound=:rightCount AND belowCount=:belowCount AND distanceToPig=:distanceToPig AND trajectorytype=:trajectoryType;")
    double getQValue(
            @Bind("stateId") int stateId,
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

    @SqlQuery("SELECT stateId, targetObjectType, aboveCount, leftCount, rightCount, belowCount, distanceToPig, trajectoryType, targetObject FROM q_values WHERE stateId=:stateId ORDER BY q_value DESC LIMIT 1;")
    Action getBestAction(@Bind("stateId") int stateId);

    @SqlQuery("SELECT stateId, targetObjectType, aboveCount, leftCount, rightCount, belowCount, distanceToPig,  trajectoryType, targetObject FROM q_values WHERE stateId=:stateId ORDER BY RANDOM() LIMIT 1;")
    Action getRandomAction(@Bind("stateId") int stateId);

    @SqlQuery("SELECT COUNT(*) FROM q_values WHERE stateId=:stateId GROUP BY targetObjectType, aboveCount, leftCount, rightCount, belowCount, distanceToPig,  trajectoryType;")
    int getActionCount(@Bind("stateId") int stateId);

    /**
     * closes the connection
     */
    void close();

    class ActionMapper implements ResultSetMapper<Action> {
        public Action map(int index, ResultSet resultSet, StatementContext ctx) throws SQLException {
            ABObject targetObject = new ABObject();
            targetObject.type = ABType.valueOf(resultSet.getString("targetObjectType"));
            targetObject.setObjectsAround(resultSet.getInt("aboveCount"), resultSet.getInt("leftCount"), resultSet.getInt("rightCount"), resultSet.getInt("belowCount"), resultSet.getDouble("distanceToPig"));

            return new Action(targetObject, ABObject.TrajectoryType.valueOf(resultSet.getString("trajectoryType")));
        }
    }
}