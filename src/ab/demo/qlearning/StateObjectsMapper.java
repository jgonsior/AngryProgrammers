package ab.demo.qlearning;

import ab.demo.qlearning.StateObject;
import org.skife.jdbi.v2.tweak.ResultSetMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.skife.jdbi.v2.StatementContext;


public class StateObjectsMapper  implements ResultSetMapper<StateObject> {

	@Override
    public StateObject map(int row, ResultSet rs, StatementContext ctx) throws SQLException { 
    	return new StateObject(rs.getInt("stateId"), rs.getString("objectIds") );
    }
}