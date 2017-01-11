package ab.demo.DAO;

import ab.demo.other.Action;
import ab.vision.ABObject;
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

    @SqlUpdate("CREATE TABLE IF NOT EXISTS q_values (q_value DOUBLE PRECISION, stateId INT, actionId INT, trajectoryType VARCHAR(4), targetObject VARCHAR(150), PRIMARY KEY(stateId, actionId))")
    void createTable();

    @SqlUpdate("UPDATE q_values SET q_value=:q_value WHERE stateId=:stateId AND actionId=:actionId;")
    void updateQValue(@Bind("q_value") double qValue, @Bind("stateId") int stateId, @Bind("actionId") int actionId);

    @SqlUpdate("INSERT INTO q_values(q_value, stateId, actionId, trajectoryType, targetObject) VALUES (:q_value, :stateId, :actionId, :trajectoryType, :targetObject);")
    void insertNewAction(@Bind("q_value") double qValue, @Bind("stateId") int stateId, @Bind("actionId") int actionId, @Bind("trajectoryType") String trajectoryType, @Bind("targetObject") String targetObject);

    @SqlQuery("SELECT q_value FROM q_values WHERE stateId=:stateId AND actionId=:actionId;")
    double getQValue(@Bind("stateId") int stateId, @Bind("actionId") int actionId);

    @SqlQuery("SELECT MAX(q_value) FROM q_values WHERE stateId=:stateId;")
    double getHighestQValue(@Bind("stateId") int stateId);

    @SqlQuery("SELECT actionId, trajectoryType, targetObject FROM q_values WHERE stateId=:stateId ORDER BY q_value DESC LIMIT 1;")
    Action getBestAction(@Bind("stateId") int stateId);

    @SqlQuery("SELECT actionId, trajectoryType, targetObject FROM q_values WHERE stateId=:stateId ORDER BY RANDOM() LIMIT 1;")
    Action getRandomAction(@Bind("stateId") int stateId);

    @SqlQuery("SELECT COUNT(actionId) FROM q_values WHERE stateId=:stateId;")
    int getActionCount(@Bind("stateId") int stateId);

    /**
     * closes the connection
     */
    void close();

    class ActionMapper implements ResultSetMapper<Action> {
        public Action map(int index, ResultSet resultSet, StatementContext ctx) throws SQLException {
            return new Action(resultSet.getInt("actionId"), ABObject.TrajectoryType.valueOf(resultSet.getString("trajectoryType")), null);
        }
    }
}