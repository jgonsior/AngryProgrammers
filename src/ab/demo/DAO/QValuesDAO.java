package ab.demo.DAO;

import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;

/**
 * @author jgonsior
 */
public interface QValuesDAO {

    @SqlUpdate("CREATE TABLE IF NOT EXISTS q_values (q_value DOUBLE PRECISION, state INT, action INT, trajectoryType VARCHAR(4), actionObject VARCHAR(150), PRIMARY KEY(state, action))")
    void createQValuesTable();

    @SqlUpdate("UPDATE q_values SET q_value=:q_value WHERE state=:state AND action=:action;")
    void updateQValue(@Bind("q_value") double qValue, @Bind("state") int state, @Bind("action") int action);

    @SqlUpdate("INSERT INTO q_values(q_value, state, action, trajectoryType, actionObject) VALUES (:q_value, :state, :action, :trajectoryType, :actionObject);")
    void insertNewAction(@Bind("q_value") double qValue, @Bind("state") int state, @Bind("action") int action, @Bind("trajectoryType") String trajectoryType, @Bind("actionObject") String actionObject);

    @SqlQuery("SELECT q_value FROM q_values WHERE state=:state AND action=:action;")
    double getQValue(@Bind("state") int state, @Bind("action") int action);

    @SqlQuery("SELECT MAX(q_value) FROM q_values WHERE state=:state;")
    double getHighestQValue(@Bind("state") int state);

    @SqlQuery("SELECT action FROM q_values WHERE state=:state ORDER BY q_value DESC LIMIT 1;")
    int getBestAction(@Bind("state") int state);

    @SqlQuery("SELECT action FROM q_values WHERE state=:state ORDER BY RANDOM() LIMIT 1;")
    int getRandomAction(@Bind("state") int state);

    @SqlQuery("SELECT COUNT(action) FROM q_values WHERE state=:state;")
    int getActionCount(@Bind("state") int state);

    /**
     * closes the connection
     */
    void close();
}