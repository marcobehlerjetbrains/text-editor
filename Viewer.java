import com.sun.jna.*;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

public class Viewer {

    private static final int ARROW_UP = 1000, ARROW_DOWN = 1001, ARROW_LEFT = 1002,
            ARROW_RIGHT = 1003,
            HOME = 1004,
            END = 1005,
            PAGE_UP = 1006,
            PAGE_DOWN = 1007,
            DEL = 1008,

            BACKSPACE = 127;



    private static int rows = 10;
    private static int columns = 10;

    private static int cursorX = 0, offsetX = 0, cursorY = 0, offsetY = 0;

    private static Terminal terminal =
            Platform.isWindows() ? new WindowsTerminal() :
                    Platform.isMac() ? new MacOsTerminal() : new UnixTerminal();

    private static List<String> content = List.of();
    private static String statusMessage;


    public static void main(String[] args) throws IOException {
        // System.out.println("Hello World");
        /*System.out.println("\033[4;44;31mHello World\033[0mHello");
        System.out.println("\033[2J");
        System.out.println("\033[5H");*/


        openFile(args);
        initEditor();

        while (true) {

            refreshScreen();
            int key = readKey();
            handleKey(key);
        }

    }

    private static void scroll() {
        if (cursorY >= rows + offsetY) {
            offsetY = cursorY - rows + 1;
        } else if (cursorY < offsetY) {
            offsetY = cursorY;
        }

        if (cursorX >= columns + offsetX) {
            offsetX = cursorX - columns + 1;
        } else if (cursorX < offsetX) {
            offsetX = cursorX;
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
        terminal.enableRawMode();
        WindowSize windowSize = terminal.getWindowSize();
        columns = windowSize.columns();
        rows = windowSize.rows() - 1;
    }

    private static void refreshScreen() {
        scroll();
        StringBuilder builder = new StringBuilder();

        drawCusorInTopLeft(builder);
        drawContent(builder);
        drawStatusBar(builder);
        drawCursor(builder);
        System.out.print(builder);
    }

    private static void drawCusorInTopLeft(StringBuilder builder) {
        builder.append("\033[H");
    }

    private static void drawCursor(StringBuilder builder) {
        builder.append(String.format("\033[%d;%dH", cursorY - offsetY + 1, cursorX - offsetX + 1));
    }

    public static void setStatusMessage(String message) {
        statusMessage = message;
    }

    public static void clearStatusMessage() {
        statusMessage = null;
    }


    public enum SearchDirection {
        FORWARDS, BACKWARDS
    }

    static int lastMatch = -1;

    static SearchDirection searchDirection = SearchDirection.FORWARDS;


    public static void editorFind() {
        prompt("Search: %s (Use ESC/Arrows/Enter)", (query, key) -> {
            if (query == null || query.isBlank()) {
                lastMatch = -1;
                searchDirection = SearchDirection.FORWARDS;
                return;
            }
            if (key == ARROW_RIGHT || key == ARROW_DOWN ) {
                searchDirection = SearchDirection.FORWARDS;
            } else if (key == ARROW_LEFT || key == ARROW_UP) {
                searchDirection = SearchDirection.BACKWARDS;
            } else {
                lastMatch = -1;
                searchDirection = SearchDirection.FORWARDS;
            }


            if (lastMatch == -1) searchDirection = SearchDirection.FORWARDS;
            int current = lastMatch;

            for (int i = 0; i < content.size(); i++) {
                current += searchDirection == SearchDirection.FORWARDS ? 1 : -1;
                if (current == -1) {
                    current = content.size() - 1;
                } else if (current == content.size()) {
                    current = 0;
                }

                String line = content.get(current);

                int match = line.indexOf(query);

                if (match != -1) {
                    lastMatch = current;
                    cursorY = current;
                    cursorX = match;
                    offsetY = content.size();
                    return;
                }
            }
        });
    }

    private static void prompt(String initialMessage, BiConsumer<String, Integer> callback) {
        String message = initialMessage;

        StringBuilder userInputBuilder = new StringBuilder();

        while (true) {
            setStatusMessage(message);
            refreshScreen();

            int key;
            try {
                key = readKey();
            } catch (IOException e) {
                e.printStackTrace();
                key = '\033';
            }

            if (key == DEL || key == ctrl_key('h') || key == BACKSPACE ) {
                if (userInputBuilder.length() > 0) {
                    userInputBuilder.deleteCharAt(userInputBuilder.length() - 1);
                    message = userInputBuilder.toString();
                }
            }
            else if (key == '\033') {  // escap
                clearStatusMessage();
                callback.accept(userInputBuilder.toString(), key);
                return;
            } else if (key == 13) { // user pressed enter
                clearStatusMessage();
                callback.accept(userInputBuilder.toString(), key);
                return;
            } else if (!Character.isISOControl(key)  && key < 128){
                userInputBuilder.append((char) key);
                message = userInputBuilder.toString();
            }

            callback.accept(userInputBuilder.toString(), key);
        }
    }

    private static void drawStatusBar(StringBuilder builder) {
        String toDraw = statusMessage != null ? statusMessage : ("Rows: " + rows + "X:" + cursorX + " Y: " + cursorY);

        builder.append("\033[7m")
                .append(toDraw)
                .append(" ".repeat(Math.max(0, columns - toDraw.length())))
                .append("\033[0m");
    }

    private static void drawContent(StringBuilder builder) {
        for (int i = 0; i < rows; i++) {
            int fileI = offsetY + i;
            if (fileI >= content.size()) {
                builder.append("~");
            } else {
                String line = content.get(fileI);
                int lengthToDraw = line.length() - offsetX;

                if (lengthToDraw < 0) {
                    lengthToDraw = 0;
                }
                if (lengthToDraw > columns) {
                    lengthToDraw = columns;
                }

                if (lengthToDraw > 0) {
                    builder.append(line, offsetX, offsetX + lengthToDraw);
                }


            }
            builder.append("\033[K\r\n");
        }
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
                        default:
                            yield yetAnotherKey;
                    }
                }
                default -> yetAnotherKey;
            };
        } else { //if (nextKey == 'O') {  e.g. escpOH == HOME
            return switch (yetAnotherKey) {
                case 'H' -> HOME;
                case 'F' -> END;
                default -> yetAnotherKey;
            };
        }
    }


    public static int ctrl_key(int key) {
        return key & 0x1f;
    }

    private static void handleKey(int key) {
        if (key == ctrl_key('q')) {
            exit();
        } else if (key == ctrl_key('f')) {
            editorFind();
        } else if (List.of(ARROW_UP, ARROW_DOWN, ARROW_LEFT, ARROW_RIGHT, HOME, END, PAGE_UP, PAGE_DOWN).contains(key)) {
            moveCursor(key);
        }
        /*else {
            System.out.print((char) + key + " -> (" + key + ")\r\n");
        }*/
    }

    private static void exit() {
        System.out.print("\033[2J");
        System.out.print("\033[H");
        terminal.disableRawMode();
        System.exit(0);
    }


    private static void moveCursor(int key) {
        String line = currentLine();
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
            }
            case ARROW_RIGHT -> {
                if (line != null && cursorX < line.length()) {
                    cursorX++;
                }
            }
            case PAGE_UP, PAGE_DOWN -> {

                if (key == PAGE_UP) {
                    moveCursorToTopOffScreen();
                } else if (key == PAGE_DOWN) {
                    moveCursorToBottomOffScreen();
                }

                for (int i = 0; i < rows; i++) {
                    moveCursor(key == PAGE_UP ? ARROW_UP : ARROW_DOWN);
                }

            }
            case HOME -> cursorX = 0;
            case END -> {
                if (line != null) {
                    cursorX = line.length();
                }
            }
        }

        String newLine = currentLine();
        if (newLine != null && cursorX > newLine.length()) {
            cursorX = newLine.length();
        }
    }

    private static String currentLine() {
        return cursorY < content.size() ? content.get(cursorY) : null;
    }

    private static void moveCursorToTopOffScreen() {
        cursorY = offsetY;
    }

    private static void moveCursorToBottomOffScreen() {
        cursorY = offsetY + rows - 1;
        if (cursorY > content.size()) cursorY = content.size();
    }


}

interface Terminal {
    void enableRawMode();

    void disableRawMode();

    WindowSize getWindowSize();
}

class UnixTerminal implements Terminal {

    private static LibC.Termios originalAttributes;

    @Override
    public void enableRawMode() {
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

    @Override
    public void disableRawMode() {
        LibC.INSTANCE.tcsetattr(LibC.SYSTEM_OUT_FD, LibC.TCSAFLUSH, originalAttributes);
    }

    @Override
    public WindowSize getWindowSize() {
        final LibC.Winsize winsize = new LibC.Winsize();

        final int rc = LibC.INSTANCE.ioctl(LibC.SYSTEM_OUT_FD, LibC.INSTANCE.TIOCGWINSZ, winsize);

        if (rc != 0) {
            System.err.println("ioctl failed with return code[={}]" + rc);
            System.exit(1);
        }
        return new WindowSize(winsize.ws_row, winsize.ws_col);
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

        int ioctl(int fd, int opt, Winsize winsize) throws LastErrorException;

    }

}


class MacOsTerminal implements Terminal {

    private static LibC.Termios originalAttributes;

    @Override
    public void enableRawMode() {
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

    @Override
    public void disableRawMode() {
        LibC.INSTANCE.tcsetattr(LibC.SYSTEM_OUT_FD, LibC.TCSAFLUSH, originalAttributes);
    }

    @Override
    public WindowSize getWindowSize() {
        final LibC.Winsize winsize = new LibC.Winsize();

        final int rc = LibC.INSTANCE.ioctl(LibC.SYSTEM_OUT_FD, LibC.INSTANCE.TIOCGWINSZ, winsize);

        if (rc != 0) {
            System.err.println("ioctl failed with return code[={}]" + rc);
            System.exit(1);
        }
        return new WindowSize(winsize.ws_row, winsize.ws_col);
    }


    interface LibC extends Library {

        int SYSTEM_OUT_FD = 0;
        int ISIG = 1, ICANON = 2, ECHO = 10, TCSAFLUSH = 2,
                IXON = 2000, ICRNL = 400, IEXTEN = 100000, OPOST = 1, VMIN = 6, VTIME = 5, TIOCGWINSZ = 0x40087468;

        // we're loading the C standard library for POSIX systems
        LibC INSTANCE = Native.load("c", LibC.class);

        @Structure.FieldOrder(value = {"ws_row", "ws_col", "ws_xpixel", "ws_ypixel"})
        class Winsize extends Structure {
            public short ws_row, ws_col, ws_xpixel, ws_ypixel;
        }


        @Structure.FieldOrder(value = {"c_iflag", "c_oflag", "c_cflag", "c_lflag", "c_cc"})
        class Termios extends Structure {
            public long c_iflag, c_oflag, c_cflag, c_lflag;

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

        int ioctl(int fd, int opt, Winsize winsize) throws LastErrorException;

    }

}

class WindowsTerminal implements Terminal {

    private IntByReference inMode;
    private IntByReference outMode;

    @Override
    public void enableRawMode() {
        Pointer inHandle = Kernel32.INSTANCE.GetStdHandle(Kernel32.STD_INPUT_HANDLE);

        inMode = new IntByReference();
        Kernel32.INSTANCE.GetConsoleMode(inHandle, inMode);

        int inMode;
        inMode = this.inMode.getValue() & ~(
                Kernel32.ENABLE_ECHO_INPUT
                        | Kernel32.ENABLE_LINE_INPUT
                        | Kernel32.ENABLE_MOUSE_INPUT
                        | Kernel32.ENABLE_WINDOW_INPUT
                        | Kernel32.ENABLE_PROCESSED_INPUT
                        | Kernel32.ENABLE_PROCESSED_INPUT
        );

        inMode |= Kernel32.ENABLE_VIRTUAL_TERMINAL_INPUT;


        Kernel32.INSTANCE.SetConsoleMode(inHandle, inMode);

        Pointer outHandle = Kernel32.INSTANCE.GetStdHandle(Kernel32.STD_OUTPUT_HANDLE);
        outMode = new IntByReference();
        Kernel32.INSTANCE.GetConsoleMode(outHandle, outMode);

        int outMode = this.outMode.getValue();
        outMode |= Kernel32.ENABLE_VIRTUAL_TERMINAL_PROCESSING;
        outMode |= Kernel32.ENABLE_PROCESSED_OUTPUT;
        Kernel32.INSTANCE.SetConsoleMode(outHandle, outMode);

    }


    @Override
    public void disableRawMode() {
        Pointer inHandle = Kernel32.INSTANCE.GetStdHandle(Kernel32.STD_INPUT_HANDLE);
        Kernel32.INSTANCE.SetConsoleMode(inHandle, inMode.getValue());

        Pointer outHandle = Kernel32.INSTANCE.GetStdHandle(Kernel32.STD_OUTPUT_HANDLE);
        Kernel32.INSTANCE.SetConsoleMode(outHandle, outMode.getValue());
    }


    @Override
    public WindowSize getWindowSize() {
        final Kernel32.CONSOLE_SCREEN_BUFFER_INFO info = new Kernel32.CONSOLE_SCREEN_BUFFER_INFO();
        final Kernel32 instance = Kernel32.INSTANCE;
        final Pointer handle = Kernel32.INSTANCE.GetStdHandle(Kernel32.STD_OUTPUT_HANDLE);
        instance.GetConsoleScreenBufferInfo(handle, info);
        return new WindowSize(info.windowHeight(), info.windowWidth());
    }

    interface Kernel32 extends StdCallLibrary {

        Kernel32 INSTANCE = Native.load("kernel32", Kernel32.class);

        /**
         * The CryptUIDlgSelectCertificateFromStore function displays a dialog box
         * that allows the selection of a certificate from a specified store.
         *
         * @param hCertStore Handle of the certificate store to be searched.
         * @param hwnd Handle of the window for the display. If NULL,
         * defaults to the desktop window.
         * @param pwszTitle String used as the title of the dialog box. If
         * NULL, the default title, "Select Certificate,"
         * is used.
         * @param pwszDisplayString Text statement in the selection dialog box. If
         * NULL, the default phrase, "Select a certificate
         * you want to use," is used.
         * @param dwDontUseColumn Flags that can be combined to exclude columns of
         * the display.
         * @param dwFlags Currently not used and should be set to 0.
         * @param pvReserved Reserved for future use.
         * @return Returns a pointer to the selected certificate context. If no
         * certificate was selected, NULL is returned. When you have
         * finished using the certificate, free the certificate context by
         * calling the CertFreeCertificateContext function.
         */
        public static final int ENABLE_VIRTUAL_TERMINAL_PROCESSING = 0x0004, ENABLE_PROCESSED_OUTPUT = 0x0001;

        int ENABLE_LINE_INPUT = 0x0002;
        int ENABLE_PROCESSED_INPUT = 0x0001;
        int ENABLE_ECHO_INPUT = 0x0004;
        int ENABLE_MOUSE_INPUT = 0x0010;
        int ENABLE_WINDOW_INPUT = 0x0008;
        int ENABLE_QUICK_EDIT_MODE = 0x0040;
        int ENABLE_INSERT_MODE = 0x0020;

        int ENABLE_EXTENDED_FLAGS = 0x0080;

        int ENABLE_VIRTUAL_TERMINAL_INPUT = 0x0200;


        int STD_OUTPUT_HANDLE = -11;
        int STD_INPUT_HANDLE = -10;
        int DISABLE_NEWLINE_AUTO_RETURN = 0x0008;

        // BOOL WINAPI GetConsoleScreenBufferInfo(
        // _In_   HANDLE hConsoleOutput,
        // _Out_  PCONSOLE_SCREEN_BUFFER_INFO lpConsoleScreenBufferInfo);
        void GetConsoleScreenBufferInfo(
                Pointer in_hConsoleOutput,
                CONSOLE_SCREEN_BUFFER_INFO out_lpConsoleScreenBufferInfo)
                throws LastErrorException;

        void GetConsoleMode(
                Pointer in_hConsoleOutput,
                IntByReference out_lpMode)
                throws LastErrorException;

        void SetConsoleMode(
                Pointer in_hConsoleOutput,
                int in_dwMode) throws LastErrorException;

        Pointer GetStdHandle(int nStdHandle);

        // typedef struct _CONSOLE_SCREEN_BUFFER_INFO {
        //   COORD      dwSize;
        //   COORD      dwCursorPosition;
        //   WORD       wAttributes;
        //   SMALL_RECT srWindow;
        //   COORD      dwMaximumWindowSize;
        // } CONSOLE_SCREEN_BUFFER_INFO;
        class CONSOLE_SCREEN_BUFFER_INFO extends Structure {


            public COORD dwSize;
            public COORD dwCursorPosition;
            public short wAttributes;
            public SMALL_RECT srWindow;
            public COORD dwMaximumWindowSize;

            private static String[] fieldOrder = {"dwSize", "dwCursorPosition", "wAttributes", "srWindow", "dwMaximumWindowSize"};

            @Override
            protected java.util.List<String> getFieldOrder() {
                return java.util.Arrays.asList(fieldOrder);
            }

            public int windowWidth() {
                return this.srWindow.width() + 1;
            }

            public int windowHeight() {
                return this.srWindow.height() + 1;
            }
        }

        // typedef struct _COORD {
        //    SHORT X;
        //    SHORT Y;
        //  } COORD, *PCOORD;
        class COORD extends Structure implements Structure.ByValue {
            public COORD() {
            }

            public COORD(short X, short Y) {
                this.X = X;
                this.Y = Y;
            }

            public short X;
            public short Y;

            private static String[] fieldOrder = {"X", "Y"};

            @Override
            protected java.util.List<String> getFieldOrder() {
                return java.util.Arrays.asList(fieldOrder);
            }
        }

        // typedef struct _SMALL_RECT {
        //    SHORT Left;
        //    SHORT Top;
        //    SHORT Right;
        //    SHORT Bottom;
        //  } SMALL_RECT;
        class SMALL_RECT extends Structure {
            public SMALL_RECT() {
            }

            public SMALL_RECT(SMALL_RECT org) {
                this(org.Top, org.Left, org.Bottom, org.Right);
            }

            public SMALL_RECT(short Top, short Left, short Bottom, short Right) {
                this.Top = Top;
                this.Left = Left;
                this.Bottom = Bottom;
                this.Right = Right;
            }

            public short Left;
            public short Top;
            public short Right;
            public short Bottom;

            private static String[] fieldOrder = {"Left", "Top", "Right", "Bottom"};

            @Override
            protected java.util.List<String> getFieldOrder() {
                return java.util.Arrays.asList(fieldOrder);
            }

            public short width() {
                return (short) (this.Right - this.Left);
            }

            public short height() {
                return (short) (this.Bottom - this.Top);
            }

        }


    }
}


record WindowSize(int rows, int columns) {

}


/**/