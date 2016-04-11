package ssn;

import java.io.IOException;
import java.sql.SQLException;

public class Main {
    private static final String USAGE = "Available subcommands:\n"
                    + "\tmaster  mapper  reducer\n"
                    + "For help on a specific subcommand type <subcommand> -h";
    
    public static void main(String[] args) throws IOException, SQLException {
        if (args.length < 1) {
            System.out.println(USAGE);
            System.exit(0);
        }
        switch (args[0]) {
            case "master":
                Master.main(args); break;
            case "mapper":
                Mapper.main(args); break;
            case "reducer":
                Reducer.main(args); break;
            default:
                System.out.println(USAGE);
        }
    }
}
