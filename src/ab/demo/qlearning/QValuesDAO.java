package ab.demo.qlearning;

import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;

/**
 * @author jgonsior
 */
public interface QValuesDAO {

    @SqlUpdate("CREATE TABLE q_values (q_value DOUBLE, state VARCHAR(20), action DOUBLE, PRIMARY KEY(state, action))")
    void createQValuesTable();

    @SqlUpdate("UPDATE q_value FROM q_values SET q_value=:q_value WHERE state=:state AND action=:action;")
    void updateQValue(@Bind("q_value") double qValue, @Bind("state") String state, @Bind("action") String action);

    @SqlQuery("SELECT q_value FROM q_values WHERE state=:state AND action=:action;")
    double getQValue(@Bind("state") String state, @Bind("action") String action);

    @SqlQuery("SELECT MAX(q_value) FROM q_values WHERE state=:state;")
    double getHighestQValue(@Bind("state") String state);

    @SqlQuery("SELECT action FROM q_values WHERE state=:state ORDER BY q_value DESC LIMIT 1;")
    String getBestAction(@Bind("state") String state);

    @SqlQuery("SELECT action FROM q_values WHERE state=:state ORDER BY RAND() LIMIT 1;")
    String getRandomAction(@Bind("state") String state);

    /**
     * closes the connection
     */
    void close();
}
