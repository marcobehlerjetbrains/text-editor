import com.sun.jna.LastErrorException;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Structure;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public class Viewer {

    private static final int ARROW_UP = 1000
            ,ARROW_DOWN = 1001
            ,ARROW_LEFT = 1002,
            ARROW_RIGHT = 1003,
            HOME = 1004,
            END = 1005,
            PAGE_UP = 1006,
            PAGE_DOWN = 1007,
            DEL = 1008;
    private static LibC.Termios originalAttributes;
    private static int rows = 10;
    private static int columns = 10;

    private static int cursorX = 0, cursorY = 0, offsetY = 0;

    private static List<String> content = List.of();

    public static void main(String[] args) throws IOException {
       // System.out.println("Hello World");
        /*System.out.println("\033[4;44;31mHello World\033[0mHello");
        System.out.println("\033[2J");
        System.out.println("\033[5H");*/


        openFile(args);
        enableRawMode();
        initEditor();

        while (true){
      /*      scroll();
            refreshScreen();*/
            int key = readKey();
            handleKey(key);
        }

    }

    private static void scroll() {
        if (cursorY >= rows + offsetY) {
            offsetY = cursorY - rows + 1;
        }
        else if (cursorY < offsetY) {
            offsetY = cursorY;
        }
    }

    private static void openFile(String[] args) {
        if (args.length == 1) {
            String filename = args[0];
            Path path = Path.of(filename);
            if (Files.exists(path)) {
                try (Stream<String> stream = Files.lines(path)) {
                    content = stream.toList();
                } catch (IOException e) {
                    // TODO
                }
            }

        }
    }

    private static void initEditor() {
        LibC.Winsize windowSize = getWindowSize();
        columns = windowSize.ws_col;
        rows = windowSize.ws_row - 1;
        System.out.println("columns = " + columns + " rows " + rows);
    }

    private static void refreshScreen() {
        StringBuilder builder = new StringBuilder();

        moveCursorToTopLeft(builder);
        drawContent(builder);
        drawStatusBar(builder);
        drawCursor(builder);
        System.out.print(builder);
    }

    private static void moveCursorToTopLeft(StringBuilder builder) {
        builder.append("\033[H");
    }

    private static void drawCursor(StringBuilder builder) {
        builder.append(String.format("\033[%d;%dH", cursorY - offsetY + 1, cursorX + 1));
    }

    private static void drawStatusBar(StringBuilder builder) {
        String statusMessage = "Rows: " + rows + "X:" + cursorX + " Y: " + cursorY;
                builder.append("\033[7m")
                .append(statusMessage)
                .append(" ".repeat(Math.max(0, columns - statusMessage.length())))
                .append("\033[0m");
    }

    private static void drawContent(StringBuilder builder) {
        for (int i = 0; i < rows; i++) {
            int fileI = offsetY + i;
            if (fileI >= content.size()) {
                builder.append("~");
            } else {
                builder.append(content.get(fileI));
            }
            builder.append("\033[K\r\n");
        }
    }


    private static int readKey() throws IOException {
        int key = System.in.read();
        System.out.println("just read in a keyu mupped");
        return key;
        /*if (key != '\033') {
            return key;
        }

        int nextKey = System.in.read();
        if (nextKey != '[' && nextKey != 'O') {
            return nextKey;
        }

        int yetAnotherKey = System.in.read();

        if (nextKey == '[') {
            return switch (yetAnotherKey) {
                case 'A' -> ARROW_UP;  // e.g. esc[A == arrow_up
                case 'B' -> ARROW_DOWN;
                case 'C' -> ARROW_RIGHT;
                case 'D' -> ARROW_LEFT;
                case 'H' -> HOME;
                case 'F' -> END;
                case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> {  // e.g: esc[5~ == page_up
                    int yetYetAnotherChar = System.in.read();
                    if (yetYetAnotherChar != '~') {
                        yield yetYetAnotherChar;
                    }
                    switch (yetAnotherKey) {
                        case '1':
                        case '7':
                            yield HOME;
                        case '3':
                            yield DEL;
                        case '4':
                        case '8':
                            yield END;
                        case '5':
                            yield PAGE_UP;
                        case '6':
                            yield PAGE_DOWN;
                        default: yield yetAnotherKey;
                    }
                }
                default -> yetAnotherKey;
            };
        } else  { //if (nextKey == 'O') {  e.g. escpOH == HOME
            return switch (yetAnotherKey) {
                case 'H' -> HOME;
                case 'F' -> END;
                default -> yetAnotherKey;
            };
        }*/
    }

    private static void handleKey(int key) {
        if (key == 'q') {
            exit();
        } else {
            System.out.print((char) key + " ->  (" + key + ")\r\n");
        }
        /*else if (List.of(ARROW_UP, ARROW_DOWN, ARROW_LEFT, ARROW_RIGHT, HOME, END).contains(key)) {
            moveCursor(key);
        }*/
        /*else {
            System.out.print((char) + key + " -> (" + key + ")\r\n");
        }*/
    }

    private static void exit() {
        System.out.print("\033[2J");
        System.out.print("\033[H");
        LibC.INSTANCE.tcsetattr(LibC.SYSTEM_OUT_FD, LibC.TCSAFLUSH, originalAttributes);
        System.exit(0);
    }

    private static void moveCursor(int key) {
            switch (key) {
                case ARROW_UP -> {
                    if (cursorY > 0) {
                        cursorY--;
                    }
                }
                case ARROW_DOWN -> {
                    if (cursorY < content.size()) {
                        cursorY++;
                    }
                }
                case ARROW_LEFT -> {
                    if (cursorX > 0) {
                        cursorX--;
                    }
                } case ARROW_RIGHT -> {
                    if (cursorX < columns - 1) {
                        cursorX++;
                    }
                }
                case HOME -> cursorX = 0;
                case END -> cursorX = columns - 1;
            }
    }


    private static void enableRawMode() {
        LibC.Termios termios = new LibC.Termios();
        int rc = LibC.INSTANCE.tcgetattr(LibC.SYSTEM_OUT_FD, termios);

        if (rc != 0) {
            System.err.println("There was a problem calling tcgetattr");
            System.exit(rc);
        }

        System.out.println("Enabled raw mode");

        originalAttributes = LibC.Termios.of(termios);
        System.out.println("originalAttributes = " + originalAttributes);

        System.out.println(termios.c_lflag  + " ------ " + (termios.c_lflag & ~LibC.ECHO));

        termios.c_lflag |= ~(LibC.ECHO | LibC.ICANON | LibC.IEXTEN | LibC.ISIG);
        termios.c_iflag |= ~(LibC.IXON | LibC.ICRNL);
        termios.c_oflag |= ~(LibC.OPOST);

        termios.c_cc[LibC.VMIN] = 0;
        termios.c_cc[LibC.VTIME] = 1;

        System.out.println("termios = " + termios);

        LibC.INSTANCE.tcsetattr(LibC.SYSTEM_OUT_FD, LibC.TCSAFLUSH, termios);
    }


    private static LibC.Winsize getWindowSize() {
        final LibC.Winsize winsize = new LibC.Winsize();
        try {
            final int rc = LibC.INSTANCE.ioctl(LibC.SYSTEM_OUT_FD, LibC.INSTANCE.tiocgwinsz(), winsize);

            if (rc != 0) {
                System.err.println("ioctl failed with return code[={}]" + rc);
                System.exit(1);
            }
        } catch (LastErrorException e) {
            System.err.println("ioctl failed with errno => " + e.getErrorCode());
            System.exit(1);
        }


        return winsize;
    }

}

interface LibC extends Library {

    int SYSTEM_OUT_FD = 0;
    int ISIG = 0x00000080, ICANON =  0x00000100, ECHO = 0x00000008, TCSAFLUSH = 2,
            IXON = 0x00000200, ICRNL = 0x00000100, IEXTEN = 0x00000400, OPOST = 0x00000001, VMIN = 6, VTIME = 5;

    // we're loading the C standard library for POSIX systems
    LibC INSTANCE = Native.load("c", LibC.class);

    @Structure.FieldOrder(value = {"ws_row", "ws_col", "ws_xpixel", "ws_ypixel"})
    class Winsize extends Structure {
        public short ws_row, ws_col, ws_xpixel, ws_ypixel;
    }



    @Structure.FieldOrder(value = {"c_iflag", "c_oflag", "c_cflag", "c_lflag", "c_cc"})
    class Termios extends Structure {
        public int c_iflag, c_oflag, c_cflag, c_lflag;

        public byte[] c_cc = new byte[20];

        public Termios() {
        }

        public static Termios of(Termios t) {
            Termios copy = new Termios();
            copy.c_iflag = t.c_iflag;
            copy.c_oflag = t.c_oflag;
            copy.c_cflag = t.c_cflag;
            copy.c_lflag = t.c_lflag;
            copy.c_cc = t.c_cc.clone();
            return copy;
        }

        @Override
        public String toString() {
            return "Termios{" +
                    "c_iflag=" + c_iflag +
                    ", c_oflag=" + c_oflag +
                    ", c_cflag=" + c_cflag +
                    ", c_lflag=" + c_lflag +
                    ", c_cc=" + Arrays.toString(c_cc) +
                    '}';
        }
    }


    default int tiocgwinsz() {
        String os = System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH);

        if (os.contains("mac") || os.contains("darwin")) {
            return 0x40087468;
        } else {
            return 0x5413;
        }
    }


    int tcgetattr(int fd, Termios termios) throws LastErrorException;

    int tcsetattr(int fd, int optional_actions,
                     Termios termios) throws LastErrorException;

    int ioctl(int fd, int opt, Winsize winsize) throws LastErrorException;

}

    /**/