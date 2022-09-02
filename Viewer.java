import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Structure;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
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

    public static final List<Integer> CURSOR_KEYS = List.of(ARROW_UP, ARROW_DOWN, ARROW_LEFT, ARROW_RIGHT, HOME, END, PAGE_DOWN, PAGE_UP);
    private static LibC.Termios originalAttributes;
    private static int rows;
    private static int columns;

    private static int cursorX =0, cursorY = 0, offsetY = 0, offsetX = 0;
    private static List<String> content= List.of();

    public static void main(String[] args) throws IOException {
       // System.out.println("Hello World");
        /*System.out.println("\033[4;44;31mHello World\033[0mHello");
        System.out.println("\033[2J");
        System.out.println("\033[5H");*/

        if (args.length == 1){
            openFile(args);
        }
        enableRawMode();
        initEditor();

        while (true){
            scroll();
            refreshScreen();
            int key = readKey();
            handleKey(key);
        }

    }

    private static void scroll() {
        if (cursorY >=  rows + offsetY) {
            offsetY = cursorY - rows + 1;
        }
        else if (cursorY < offsetY) {
            offsetY = cursorY;
        }

        if (cursorX >=  columns + offsetX) {
            offsetX = cursorX - columns + 1;
        }
        else if (cursorX < offsetX) {
            offsetX = cursorX;
        }
    }

    private static void openFile(String[] args) {
        Path file = Path.of(args[0]);
        if (Files.exists(file)){
            try (Stream<String> lines = Files.lines(file)) {
                content = lines.collect(Collectors.toList());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void initEditor() {
        LibC.Winsize windowSize = getWindowSize();
        columns = windowSize.ws_col;
        rows = windowSize.ws_row - 1;
    }

    private static void refreshScreen() {
        StringBuilder builder = new StringBuilder();
        //builder.append("\033[2J");

        drawLines(builder);
        drawStatusMessage(builder);
        drawCursor(builder);

        System.out.print(builder);
    }

    private static void drawCursor(StringBuilder builder) {
        builder.append(String.format("\033[%d;%dH", cursorY - offsetY + 1, cursorX - offsetX + 1));
    }

    private static void drawLines(StringBuilder builder) {
        builder.append("\033[H");
        for (int i = 0; i < rows; i++) {
            int fileRow = i + offsetY;
            if (fileRow >= content.size()) {
                builder.append("~");
            } else {
                builder.append(renderLine(content.get(i), offsetX, columns));
            }

            builder.append("\033[K\r\n");
        }
    }

    private static String renderLine(String line, int columnOffset, int screenColumns) {
        int length = Math.max(0, line.length() - columnOffset);
        if (length == 0) return "";

        if (length > screenColumns) length = screenColumns;
        return line.substring(columnOffset, columnOffset + length);
    }

    private static void drawStatusMessage(StringBuilder builder) {
        String statusMessage = "Rows: " + rows + "X:" + cursorX + " Y: " + cursorY + " Off Y " + offsetY;
        builder.append("\033[7m")
                .append(statusMessage)
                .append(" ".repeat(Math.max(0, columns - statusMessage.length())))
                .append("\033[0m");
    }


    private static int readKey() throws IOException {
        int key = System.in.read();

        if (key != '\033') {
            return key;
        }

        int nextKey = System.in.read();
        if (nextKey != '[' && nextKey != 'O') {
            return nextKey;
        }

        int keyAfterThat = System.in.read();

        if (nextKey == '[') {
            return switch (keyAfterThat) {
                case 'A' -> ARROW_UP;  // e.g. esc[A
                case 'B' -> ARROW_DOWN;
                case 'C' -> ARROW_RIGHT;
                case 'D' -> ARROW_LEFT;
                case 'H' -> HOME;
                case 'F' -> END;
                case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> {  // e.g: esc[1~
                    int yetAnotherChar = System.in.read();
                    if (yetAnotherChar != '~') {
                       yield yetAnotherChar;
                    }
                    switch (keyAfterThat) {
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
                        default: yield keyAfterThat;
                    }
                }
                default -> keyAfterThat;
            };
        } else  { //if (nextKey == 'O') {  e.g. escpOH
            return switch (keyAfterThat) {
                case 'H' -> HOME;
                case 'F' -> END;
                default -> keyAfterThat;
            };
        }


    }

    private static void handleKey(int key) {
        if (key == 'q') {
            System.out.print("\033[2J");
            System.out.print("\033[H");
            LibC.INSTANCE.tcsetattr(LibC.SYSTEM_OUT_FD, LibC.TCSAFLUSH, originalAttributes);
            System.exit(0);
        }
        else if (CURSOR_KEYS.contains(key)) {
            moveCursor(key);
        }
//        else {
//            System.out.print((char) + key + " -> (" + key + ")\r\n");
//        }
    }

    private static void moveCursor(int key) {
        if (key == ARROW_UP) {
            if (cursorY > 0) {
                cursorY--;
            }
        } else if (key == ARROW_DOWN) {
            if (cursorY < content.size()) {
                cursorY++;
            }
        } else if (key == ARROW_LEFT) {
            if (cursorX > 0) {
                cursorX--;
            }
        } else if (key == ARROW_RIGHT) {
            if (cursorX < content.get(cursorY).length()) {
                cursorX++;
            }
        } else if (key == HOME) {
            cursorX = 0;
        } else if (key == END) {
            cursorX = content.get(cursorY).length();
        }

        int lineLength = content.get(cursorY).length();
        if (lineLength < cursorX) {
            cursorX = lineLength;
        }
    }


    private static void enableRawMode() {
        LibC.Termios termios = new LibC.Termios();
        int rc = LibC.INSTANCE.tcgetattr(LibC.SYSTEM_OUT_FD, termios);

        if (rc != 0) {
            System.err.println("There was a problem calling tcgetattr");
            System.exit(rc);
        }

        originalAttributes = LibC.Termios.of(termios);

        termios.c_lflag &= ~(LibC.ECHO | LibC.ICANON | LibC.IEXTEN | LibC.ISIG);
        termios.c_iflag &= ~(LibC.IXON | LibC.ICRNL);
        termios.c_oflag &= ~(LibC.OPOST);

       /* termios.c_cc[LibC.VMIN] = 0;
        termios.c_cc[LibC.VTIME] = 1;*/

        LibC.INSTANCE.tcsetattr(LibC.SYSTEM_OUT_FD, LibC.TCSAFLUSH, termios);
    }

    private static LibC.Winsize getWindowSize() {
        final LibC.Winsize winsize = new LibC.Winsize();
        final int rc = LibC.INSTANCE.ioctl(LibC.SYSTEM_OUT_FD, LibC.TIOCGWINSZ, winsize);

        if (rc != 0) {
            System.err.println("ioctl failed with return code[={}]" + rc);
            System.exit(1);
        }

        return winsize;
    }

}

interface LibC extends Library {

    int SYSTEM_OUT_FD = 0;
    int ISIG = 1, ICANON = 2, ECHO = 10, TCSAFLUSH = 2,
            IXON = 2000, ICRNL = 400, IEXTEN = 100000, OPOST = 1, VMIN = 6, VTIME = 5, TIOCGWINSZ = 0x5413;

    // we're loading the C standard library for POSIX systems
    LibC INSTANCE = Native.load("c", LibC.class);

    @Structure.FieldOrder(value = {"ws_row", "ws_col", "ws_xpixel", "ws_ypixel"})
    class Winsize extends Structure {
        public short ws_row, ws_col, ws_xpixel, ws_ypixel;
    }



    @Structure.FieldOrder(value = {"c_iflag", "c_oflag", "c_cflag", "c_lflag", "c_cc"})
    class Termios extends Structure {
        public int c_iflag, c_oflag, c_cflag, c_lflag;

        public byte[] c_cc = new byte[19];

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


    int tcgetattr(int fd, Termios termios);

    int tcsetattr(int fd, int optional_actions,
                     Termios termios);

    int ioctl(int fd, int opt, Winsize winsize);

}

    /**/