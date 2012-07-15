import org.fusesource.jansi.AnsiConsole;

public class Main {

    public static final String ANSI_CLS = "\u001b[2J";
    public static final String ANSI_HOME = "\u001b[H";
    public static final String ANSI_BOLD = "\u001b[1m";
    public static final String ANSI_AT55 = "\u001b[10;10H";
    public static final String ANSI_REVERSEON = "\u001b[7m";
    public static final String ANSI_NORMAL = "\u001b[0m";
    public static final String ANSI_WHITEONBLUE = "\u001b[37;44m";

    public static void main(String[] args) {
        AnsiConsole.out.println(ANSI_BOLD + "Press a key..." + ANSI_NORMAL);
    }
}