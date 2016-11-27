package ab.demo.qlearning;

import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;

/**
 * @author jgonsior
 */
public interface QValuesDAO {

    @SqlUpdate("CREATE TABLE q_values (q_value DOUBLE, state VARCHAR(20), action DOUBLE, PRIMARY KEY(state, action))")
    void createQValuesTable();

    @SqlUpdate("UPDATE q_value FROM q_values SET q_value=:q_value WHERE state=:state AND action=:action;")
    void updateQValue(@Bind("q_value") double qValue, @Bind("state") String state, @Bind("action") String action);

    @SqlUpdate("SELECT q_value FROM q_values WHERE state=:state AND action=:action;")
    double getQValue(@Bind("state") String state, @Bind("action") String action);

    @SqlUpdate("SELECT MAX(q_value) FROM q_values WHERE state=:state;")
    double getHighestQValue(@Bind("state") String state);

    @SqlUpdate("SELECT TOP 1 action FROM q_values WHERE state=:state ORDER BY q_value DESC;")
    String getBestAction(@Bind("state") String state);

    @SqlUpdate("SELECT TOP 1 action FROM q_values WHERE state=:state ORDER BY NEWID();")
    String getRandomAction(@Bind("state") String state);

    /**
     * closes the connection
     */
    void close();


}
