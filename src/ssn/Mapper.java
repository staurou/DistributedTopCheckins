package ssn;

import java.io.IOException;
import java.sql.SQLException;

public class Mapper {
    private DataSource ds;
    
    public void initialize() throws IOException, SQLException {
        ds = new DataSource(null, "omada35", "omada35db");
    }
    
    private RequestToReducer map(RequestToMapper requestToMapper) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
