package Helper;

import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class LogFormatter extends Formatter {

    public LogFormatter(){}

    @Override
    public String format(LogRecord record) {
        return "" + new Date(record.getMillis()) + "\n"
                + "  ThreadID: " + record.getThreadID() + "\n"
                + "  Class:    " + record.getSourceClassName() + "\n"
                + "  Methode:  " + record.getSourceMethodName() + "\n"
                + "  " + record.getLevel()
                + ": " + record.getMessage() + "\n" + "\n";
    }
}
