/*
    MD Chess - An Android chess program.
    Copyright (C) 2011-2014  Peter Österlund, peterosterlund2@gmail.com
    Copyright (C) 2012 Leo Mayer

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.mdc.chess;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources.NotFoundException;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.view.View.OnLongClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.kalab.chess.enginesupport.ChessEngine;
import com.kalab.chess.enginesupport.ChessEngineResolver;

import org.mdc.chess.ChessBoard.SquareDecoration;
import org.mdc.chess.activities.CPUWarning;
import org.mdc.chess.activities.EditBoard;
import org.mdc.chess.activities.EditOptions;
import org.mdc.chess.activities.EditPGNLoad;
import org.mdc.chess.activities.EditPGNSave;
import org.mdc.chess.activities.LoadFEN;
import org.mdc.chess.activities.LoadScid;
import org.mdc.chess.activities.Preferences;
import org.mdc.chess.book.BookOptions;
import org.mdc.chess.engine.EngineUtil;
import org.mdc.chess.engine.UCIOptions;
import org.mdc.chess.gamelogic.ChessParseError;
import org.mdc.chess.gamelogic.GameTree.Node;
import org.mdc.chess.gamelogic.MDChessController;
import org.mdc.chess.gamelogic.Move;
import org.mdc.chess.gamelogic.Pair;
import org.mdc.chess.gamelogic.PgnToken;
import org.mdc.chess.gamelogic.Piece;
import org.mdc.chess.gamelogic.Position;
import org.mdc.chess.gamelogic.TextIO;
import org.mdc.chess.gamelogic.TimeControlData;
import org.mdc.chess.tb.Probe;
import org.mdc.chess.tb.ProbeResult;

import java.io.File;
import java.io.FileFilter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

@SuppressLint("ClickableViewAccessibility")
public class MDChess extends AppCompatActivity
        implements GUIInterface,
        ActivityCompat.OnRequestPermissionsResultCallback,
        NavigationView.OnNavigationItemSelectedListener {
    // FIXME!!! PGN view option: game continuation (for training)
    // FIXME!!! Remove invalid playerActions in PGN import (should be done in verifyChildren)
    // FIXME!!! Implement bookmark mechanism for positions in pgn files
    // FIXME!!! Add support for "Chess Leipzig" font

    // FIXME!!! Computer clock should stop if phone turned off (computer stops thinking if
    // unplugged)
    // FIXME!!! Add support for "no time control" and "hour-glass time control" as defined by the
    // PGN standard

    // FIXME!!! Add chess960 support
    // FIXME!!! Implement "hint" feature

    // FIXME!!! Show extended book info. (Win percent, number of games, performance rating, etc.)
    // FIXME!!! Green color for "main move". Red color for "don't play in tournaments" moves.
    // FIXME!!! ECO opening codes

    // FIXME!!! Option to display coordinates in border outside chess board.

    // FIXME!!! Better behavior if engine is terminated. How exactly?
    // FIXME!!! Handle PGN non-file intents with more than one game.
    // FIXME!!! Save position to fen/epd file

    // FIXME!!! Selection dialog for going into variation
    // FIXME!!! Use two engines in engine/engine games

    private final static String bookDir = "MD Chess/book";
    private final static String pgnDir = "MD Chess/pgn";
    private final static String fenDir = "MD Chess/epd";
    private final static String engineDir = "MD Chess/uci";
    private final static String gtbDefaultDir = "MD Chess/gtb";
    private final static String rtbDefaultDir = "MD Chess/rtb";
    // Unicode code points for chess pieces
    private static final String figurinePieceNames = Piece.NOTATION_PAWN + " " +
            Piece.NOTATION_KNIGHT + " " +
            Piece.NOTATION_BISHOP + " " +
            Piece.NOTATION_ROOK + " " +
            Piece.NOTATION_QUEEN + " " +
            Piece.NOTATION_KING;

    static private final int RESULT_EDITBOARD = 0;
    static private final int RESULT_SETTINGS = 1;
    static private final int RESULT_LOAD_PGN = 2;
    static private final int RESULT_LOAD_FEN = 3;
    static private final int RESULT_SELECT_SCID = 4;
    static private final int RESULT_OI_PGN_SAVE = 5;
    static private final int RESULT_OI_PGN_LOAD = 6;
    static private final int RESULT_OI_FEN_LOAD = 7;
    static private final int RESULT_GET_FEN = 8;
    static private final int RESULT_EDITOPTIONS = 9;
    static private final int SELECT_PGN_FILE_DIALOG = 7;
    static private final int SELECT_PGN_FILE_SAVE_DIALOG = 8;
    static private final int SELECT_FEN_FILE_DIALOG = 27;
    private final static int FT_NONE = 0;
    private final static int FT_PGN = 1;
    private final static int FT_SCID = 2;
    private final static int FT_FEN = 3;
    private static MDChessController ctrl = null;
    private final BookOptions bookOptions = new BookOptions();
    private final PGNOptions pgnOptions = new PGNOptions();
    private final EngineOptions engineOptions = new EngineOptions();
    private final Handler handlerTimer = new Handler();
    private final Runnable r = new Runnable() {
        public void run() {
            ctrl.updateRemainingTime();
        }
    };

    private final Handler autoModeTimer = new Handler();
    private ChessBoardPlay cb;
    private boolean mShowThinking;
    private boolean mShowStats;
    private int numPV;
    private boolean mWhiteBasedScores;
    private boolean mShowBookHints;
    private int maxNumArrows;
    private GameMode gameMode;
    private boolean mPonderMode;
    private int timeControl;
    private int movesPerSession;
    private int timeIncrement;
    private String playerName;
    private boolean boardFlipped;
    private boolean autoSwapSides;
    private boolean playerNameFlip;
    private boolean discardVariations;
    private TextView status;
    private ScrollView moveListScroll;
    private MoveListView moveList;
    private TextView thinking;
    private TextView whiteTitleText, blackTitleText, engineTitleText;
    private TextView whiteFigText, blackFigText;
    private SharedPreferences settings;
    private float scrollSensitivity;
    private boolean invertScrollDirection;
    private boolean leftHanded;

    private boolean soundEnabled;
    private MediaPlayer moveSound;
    private boolean vibrateEnabled;
    private boolean animateMoves;
    private boolean autoScrollTitle;
    private boolean showVariationLine;
    private int autoMoveDelay; // Delay in auto forward/backward mode
    private AutoMode autoMode = AutoMode.OFF;
    private final Runnable amRunnable = new Runnable() {
        @Override
        public void run() {
            switch (autoMode) {
                case BACKWARD:
                    ctrl.undoMove();
                    setAutoMode(autoMode);
                    break;
                case FORWARD:
                    ctrl.redoMove();
                    setAutoMode(autoMode);
                    break;
                case OFF:
                    break;
            }
        }
    };
    /**
     * State of WRITE_EXTERNAL_STORAGE permission.
     */
    private PermissionState storagePermission = PermissionState.UNKNOWN;
    private long lastVisibleMillis; // Time when GUI became invisible. 0 if currently visible.
    private long lastComputationMillis; // Time when engine last showed that it was computing.
    private PgnScreenText gameTextListener;
    private Typeface figNotation;
    private Typeface defaultThinkingListTypeFace;
    private boolean egtbForceReload = false;
    private String thinkingStr1 = "";
    private String thinkingStr2 = "";
    private String bookInfoStr = "";
    private String variantStr = "";
    private ArrayList<ArrayList<Move>> pvMoves = new ArrayList<>();
    private ArrayList<Move> bookMoves = null;
    private ArrayList<Move> variantMoves = null;
    // Filename of network engine to configure
    private String networkEngineToConfig = "";
    private boolean notificationActive = false;

    public static String getFilePathFromUri(Uri uri) {
        if (uri == null) {
            return null;
        }
        return uri.getPath();
    }

    private static boolean reservedEngineName(String name) {
        return "cuckoochess".equals(name) ||
                "stockfish".equals(name) ||
                name.endsWith(".ini");
    }

    private static boolean hasFenProvider(PackageManager manager) {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("application/x-chess-fen");
        List<ResolveInfo> resolvers = manager.queryIntentActivities(i, 0);
        return (resolvers != null) && (resolvers.size() > 0);
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Pair<String, String> pair = getPgnOrFenIntent();
        String intentPgnOrFen = pair.first;
        String intentFilename = pair.second;

        createDirectories();

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        settings = PreferenceManager.getDefaultSharedPreferences(this);

        setWakeLock(false);

        figNotation = Typeface.createFromAsset(getAssets(), "fonts/KlinicSlabBold.otf");
        setPieceNames(PGNOptions.PT_LOCAL);
        initUI();

        gameTextListener = new PgnScreenText(pgnOptions);
        moveList.setOnLinkClickListener(gameTextListener);
        moveList.setBackgroundColor(Color.WHITE);
        if (ctrl != null) {
            ctrl.shutdownEngine();
        }
        ctrl = new MDChessController(this, gameTextListener, pgnOptions);
        egtbForceReload = true;
        readPrefs();
        TimeControlData tcData = new TimeControlData();
        tcData.setTimeControl(timeControl, movesPerSession, timeIncrement);
        ctrl.newGame(gameMode, tcData);
        setAutoMode(AutoMode.OFF);
        {
            byte[] data = null;
            int version = 1;
            if (savedInstanceState != null) {
                data = savedInstanceState.getByteArray("gameState");
                version = savedInstanceState.getInt("gameStateVersion", version);
            } else {
                String dataStr = settings.getString("gameState", null);
                version = settings.getInt("gameStateVersion", version);
                if (dataStr != null) {
                    data = strToByteArr(dataStr);
                }
            }
            if (data != null) {
                ctrl.fromByteArray(data, version);
            }
        }
        ctrl.setGuiPaused(true);
        ctrl.setGuiPaused(false);
        ctrl.startGame();
        if (intentPgnOrFen != null) {
            try {
                ctrl.setFENOrPGN(intentPgnOrFen);
                setBoardFlip(true);
            } catch (ChessParseError e) {
                // If FEN corresponds to illegal chess position, go into edit board mode.
                try {
                    TextIO.readFEN(intentPgnOrFen);
                } catch (ChessParseError e2) {
                    if (e2.pos != null) {
                        startEditBoard(intentPgnOrFen);
                    }
                }
            }
        } else if (intentFilename != null) {
            if (intentFilename.toLowerCase(Locale.US).endsWith(".fen") ||
                    intentFilename.toLowerCase(Locale.US).endsWith(".epd")) {
                loadFENFromFile(intentFilename);
            } else {
                loadPGNFromFile(intentFilename);
            }
        }

    }

    private void setPieceNames(int pieceType) {
        if (pieceType == PGNOptions.PT_FIGURINE) {
            TextIO.setPieceNames(figurinePieceNames);
        } else {
            TextIO.setPieceNames(getString(R.string.piece_names));
        }
    }

    /**
     * Create directory structure on SD card.
     */
    private void createDirectories() {
        if (storagePermission == PermissionState.UNKNOWN) {
            String extStorage = Manifest.permission.WRITE_EXTERNAL_STORAGE;
            if (ContextCompat.checkSelfPermission(this, extStorage) ==
                    PackageManager.PERMISSION_GRANTED) {
                storagePermission = PermissionState.GRANTED;
            } else {
                ActivityCompat.requestPermissions(this, new String[]{extStorage}, 0);
                storagePermission = PermissionState.REQUESTED;
            }
        }
        if (storagePermission != PermissionState.GRANTED) {
            return;
        }

        File extDir = Environment.getExternalStorageDirectory();
        String sep = File.separator;
        boolean result;

        result = new File(extDir + sep + bookDir).mkdirs();
        Log.d("Result" + extDir + sep + bookDir, "" + result);

        result = new File(extDir + sep + pgnDir).mkdirs();
        Log.d("Result" + extDir + sep + pgnDir, "" + result);

        result = new File(extDir + sep + fenDir).mkdirs();
        Log.d("Result" + extDir + sep + fenDir, "" + result);

        result = new File(extDir + sep + engineDir).mkdirs();
        Log.d("Result" + extDir + sep + engineDir, "" + result);

        result = new File(extDir + sep + engineDir + sep + EngineUtil.openExchangeDir).mkdirs();
        Log.d("Result" + extDir + sep + engineDir + sep + EngineUtil.openExchangeDir, "" + result);

        result = new File(extDir + sep + gtbDefaultDir).mkdirs();
        Log.d("Result" + extDir + sep + gtbDefaultDir, "" + result);

        result = new File(extDir + sep + rtbDefaultDir).mkdirs();
        Log.d("Result" + extDir + sep + rtbDefaultDir, "" + result);
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] permissions,
            @NonNull int[] results) {
        if (storagePermission == PermissionState.REQUESTED) {
            if ((results.length > 0) && (results[0] == PackageManager.PERMISSION_GRANTED)) {
                storagePermission = PermissionState.GRANTED;
            } else {
                storagePermission = PermissionState.DENIED;
            }
        }
        createDirectories();
    }

    /**
     * Return true if the WRITE_EXTERNAL_STORAGE permission has been granted.
     */
    private boolean storageAvailable() {
        return storagePermission == PermissionState.GRANTED;
    }

    /**
     * Return PGN/FEN data or filename from the Intent. Both can not be non-null.
     *
     * @return Pair of PGN/FEN data and filename.
     */
    private Pair<String, String> getPgnOrFenIntent() {
        String pgnOrFen = null;
        String filename = null;
        try {
            Intent intent = getIntent();
            Uri data = intent.getData();
            if (data == null) {
                Bundle b = intent.getExtras();
                if (b != null) {
                    Object strm = b.get(Intent.EXTRA_STREAM);
                    if (strm instanceof Uri) {
                        data = (Uri) strm;
                        if ("file".equals(data.getScheme())) {
                            filename = data.getEncodedPath();
                            if (filename != null) {
                                filename = Uri.decode(filename);
                            }
                        }
                    }
                }
            }
            if (data == null) {
                if ((Intent.ACTION_SEND.equals(intent.getAction()) ||
                        Intent.ACTION_VIEW.equals(intent.getAction())) &&
                        ("application/x-chess-pgn".equals(intent.getType()) ||
                                "application/x-chess-fen".equals(intent.getType()))) {
                    pgnOrFen = intent.getStringExtra(Intent.EXTRA_TEXT);
                }
            } else {
                String scheme = intent.getScheme();
                if ("file".equals(scheme)) {
                    filename = data.getEncodedPath();
                    if (filename != null) {
                        filename = Uri.decode(filename);
                    }
                }
                if ((filename == null) &&
                        ("content".equals(scheme) ||
                                "file".equals(scheme))) {
                    ContentResolver resolver = getContentResolver();
                    InputStream in = resolver.openInputStream(intent.getData());
                    StringBuilder sb = new StringBuilder();
                    while (true) {
                        byte[] buffer = new byte[16384];
                        int len = in != null ? in.read(buffer) : 0;
                        if (len <= 0) {
                            break;
                        }
                        sb.append(new String(buffer, 0, len));
                    }
                    pgnOrFen = sb.toString();
                }
            }
        } catch (IOException e) {
            Toast.makeText(getApplicationContext(), R.string.failed_to_read_pgn_data,
                    Toast.LENGTH_SHORT).show();
        }
        return new Pair<>(pgnOrFen, filename);
    }

    private byte[] strToByteArr(String str) {
        if (str == null) {
            return null;
        }
        int nBytes = str.length() / 2;
        byte[] ret = new byte[nBytes];
        for (int i = 0; i < nBytes; i++) {
            int c1 = str.charAt(i * 2) - 'A';
            int c2 = str.charAt(i * 2 + 1) - 'A';
            ret[i] = (byte) (c1 * 16 + c2);
        }
        return ret;
    }

    private String byteArrToString(byte[] data) {
        if (data == null) {
            return null;
        }
        StringBuilder ret = new StringBuilder(32768);
        //int nBytes = data.length;
        for (byte aData : data) {
            int b = aData;
            if (b < 0) b += 256;
            char c1 = (char) ('A' + (b / 16));
            char c2 = (char) ('A' + (b & 15));
            ret.append(c1);
            ret.append(c2);
        }
        return ret.toString();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        //actionBarDrawerToggle.onConfigurationChanged(newConfig);
        reInitUI();
    }

    /**
     * Re-initialize UI when layout should change because of rotation or handedness change.
     */
    private void reInitUI() {
        ChessBoardPlay oldCB = cb;
        String statusStr = status.getText().toString();
        initUI();
        readPrefs();
        cb.cursorX = oldCB.cursorX;
        cb.cursorY = oldCB.cursorY;
        cb.cursorVisible = oldCB.cursorVisible;
        cb.setPosition(oldCB.pos);
        cb.setFlipped(oldCB.flipped);
        cb.setDrawSquareLabels(oldCB.drawSquareLabels);
        cb.oneTouchMoves = oldCB.oneTouchMoves;
        cb.toggleSelection = oldCB.toggleSelection;
        cb.highlightLastMove = oldCB.highlightLastMove;
        cb.setBlindMode(oldCB.blindMode);
        setSelection(oldCB.selectedSquare);
        cb.userSelectedSquare = oldCB.userSelectedSquare;
        setStatusString(statusStr);
        moveList.setOnLinkClickListener(gameTextListener);
        moveListUpdated();
        updateThinkingInfo();
        ctrl.updateRemainingTime();
        ctrl.updateMaterialDiffList();

    }

    /**
     * Return true if the current orientation is landscape.
     */
    private boolean landScapeView() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    /**
     * Return true if left-handed layout should be used.
     */
    private boolean leftHandedView() {
        return settings.getBoolean("leftHanded", false) && landScapeView();
    }

    /**
     * Re-read preferences settings.
     */
    private void handlePrefsChange() {
        if (leftHanded != leftHandedView()) {
            reInitUI();
        } else {
            readPrefs();
        }
        maybeAutoModeOff(gameMode);
        ctrl.setGameMode(gameMode);
    }

    private void initUI() {
        leftHanded = leftHandedView();
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.main_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open,
                R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_main);
        navigationView.setNavigationItemSelectedListener(this);

        // title lines need to be regenerated every time due to layout changes (rotations)
        View firstTitleLine = findViewById(R.id.title_line);
        whiteTitleText = (TextView) findViewById(R.id.txt_first);
        whiteTitleText.setSelected(true);
        blackTitleText = (TextView) findViewById(R.id.txt_third);
        blackTitleText.setSelected(true);
        engineTitleText = (TextView) findViewById(R.id.txt_second);

        whiteFigText = (TextView) findViewById(R.id.white_pieces);
        whiteFigText.setTypeface(figNotation);
        whiteFigText.setSelected(true);
        whiteFigText.setTextColor(whiteTitleText.getTextColors());
        blackFigText = (TextView) findViewById(R.id.black_pieces);
        blackFigText.setTypeface(figNotation);
        blackFigText.setSelected(true);
        blackFigText.setTextColor(blackTitleText.getTextColors());

        status = (TextView) findViewById(R.id.status);
        moveListScroll = (ScrollView) findViewById(R.id.scrollView);
        moveList = (MoveListView) findViewById(R.id.moveList);
        thinking = (TextView) findViewById(R.id.thinking);
        defaultThinkingListTypeFace = thinking.getTypeface();
        status.setFocusable(false);
        moveListScroll.setFocusable(false);
        moveList.setFocusable(false);
        thinking.setFocusable(false);

        class ClickListener implements OnClickListener, OnTouchListener {
            // --Commented out by Inspection (31/10/2016 10:41 PM):private float touchX = -1;

            @Override
            public void onClick(View v) {
                //boolean left = touchX <= v.getWidth() / 2.0;
                //drawerLayout.openDrawer(left ? GravityCompat.START : GravityCompat.END);
                //touchX = -1;
            }

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                //touchX = event.getX();
                return false;
            }
        }

        ClickListener listener = new ClickListener();
        firstTitleLine.setOnClickListener(listener);
        firstTitleLine.setOnTouchListener(listener);

        cb = (ChessBoardPlay) findViewById(R.id.chessboard);
        cb.setFocusable(true);
        cb.requestFocus();
        cb.setClickable(true);
        cb.setPgnOptions(pgnOptions);

        cb.setOnTouchListener(new OnTouchListener() {
            private final Handler handler = new Handler();
            private boolean pending = false;
            private final Runnable runnable = new Runnable() {
                public void run() {
                    pending = false;
                    handler.removeCallbacks(runnable);
                    ((Vibrator) getSystemService(Context.VIBRATOR_SERVICE)).vibrate(20);
                    boardMenuDialog();
                }
            };
            private boolean pendingClick = false;
            private int sq0 = -1;
            private float scrollX = 0;
            private float scrollY = 0;
            private float prevX = 0;
            private float prevY = 0;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int action = MotionEventCompat.getActionMasked(event);
                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                        handler.postDelayed(runnable, ViewConfiguration.getLongPressTimeout());
                        pending = true;
                        pendingClick = true;
                        sq0 = cb.eventToSquare(event);
                        scrollX = 0;
                        scrollY = 0;
                        prevX = event.getX();
                        prevY = event.getY();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (pending) {
                            int sq = cb.eventToSquare(event);
                            if (sq != sq0) {
                                handler.removeCallbacks(runnable);
                                pendingClick = false;
                            }
                            float currX = event.getX();
                            float currY = event.getY();
                            if (onScroll(currX - prevX, currY - prevY)) {
                                handler.removeCallbacks(runnable);
                                pendingClick = false;
                            }
                            prevX = currX;
                            prevY = currY;
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                        if (pending) {
                            pending = false;
                            handler.removeCallbacks(runnable);
                            if (!pendingClick) {
                                break;
                            }
                            int sq = cb.eventToSquare(event);
                            if (sq == sq0) {
                                if (ctrl.humansTurn()) {
                                    Move m = cb.mousePressed(sq);
                                    if (m != null) {
                                        setAutoMode(AutoMode.OFF);
                                        ctrl.makeHumanMove(m);
                                    }
                                    setEgtbHints(cb.getSelectedSquare());
                                }
                            }
                        }
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        pending = false;
                        handler.removeCallbacks(runnable);
                        break;
                }
                return true;
            }

            private boolean onScroll(float distanceX, float distanceY) {
                if (invertScrollDirection) {
                    distanceX = -distanceX;
                    distanceY = -distanceY;
                }
                if ((scrollSensitivity > 0) && (cb.sqSize > 0)) {
                    scrollX += distanceX;
                    scrollY += distanceY;
                    final float scrollUnit = cb.sqSize * scrollSensitivity;
                    if (Math.abs(scrollX) >= Math.abs(scrollY)) {
                        // Undo/redo
                        int nRedo = 0, nUndo = 0;
                        while (scrollX > scrollUnit) {
                            nRedo++;
                            scrollX -= scrollUnit;
                        }
                        while (scrollX < -scrollUnit) {
                            nUndo++;
                            scrollX += scrollUnit;
                        }
                        if (nUndo + nRedo > 0) {
                            scrollY = 0;
                            setAutoMode(AutoMode.OFF);
                        }
                        if (nRedo + nUndo > 1) {
                            boolean analysis = gameMode.analysisMode();
                            boolean human = gameMode.playerWhite() || gameMode.playerBlack();
                            if (analysis || !human) {
                                ctrl.setGameMode(new GameMode(GameMode.TWO_PLAYERS));
                            }
                        }
                        for (int i = 0; i < nRedo; i++) ctrl.redoMove();
                        for (int i = 0; i < nUndo; i++) ctrl.undoMove();
                        ctrl.setGameMode(gameMode);
                        return nRedo + nUndo > 0;
                    } else {
                        // Next/previous variation
                        int varDelta = 0;
                        while (scrollY > scrollUnit) {
                            varDelta++;
                            scrollY -= scrollUnit;
                        }
                        while (scrollY < -scrollUnit) {
                            varDelta--;
                            scrollY += scrollUnit;
                        }
                        if (varDelta != 0) {
                            scrollX = 0;
                            setAutoMode(AutoMode.OFF);
                            ctrl.changeVariation(varDelta);
                        }
                        return varDelta != 0;
                    }
                }
                return false;
            }
        });
        /*cb.setOnTrackballListener(new ChessBoard.OnTrackballListener() {

            public void onTrackballEvent(MotionEvent event) {
                if (ctrl.humansTurn()) {
                    Move m = cb.handleTrackballEvent(event);
                    if (m != null) {
                        setAutoMode(AutoMode.OFF);
                        ctrl.makeHumanMove(m);
                    }
                    setEgtbHints(cb.getSelectedSquare());
                }
            }

        });*/

        moveList.setOnLongClickListener(new OnLongClickListener() {
            public boolean onLongClick(View v) {
                moveListMenuDialog();
                return true;
            }
        });
        thinking.setOnLongClickListener(new OnLongClickListener() {
            public boolean onLongClick(View v) {
                if (mShowThinking || gameMode.analysisMode()) {
                    if (!pvMoves.isEmpty()) {
                        thinkingMenuDialog();
                    }
                }
                return true;
            }
        });

    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (ctrl != null) {
            byte[] data = ctrl.toByteArray();
            outState.putByteArray("gameState", data);
            outState.putInt("gameStateVersion", 3);
        }
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.main_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main_drawer, menu);
        View actionUndo = findViewById(R.id.action_undo);
        if (actionUndo != null) {
            actionUndo.setOnLongClickListener(
                    new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View v) {
                            goBackMenuDialog();
                            return true;
                        }
                    }
            );
        }
        View actionRedo = findViewById(R.id.action_redo);
        if (actionRedo != null) {
            actionRedo.setOnLongClickListener(
                    new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View v) {
                            goForwardMenuDialog();
                            return true;
                        }
                    }
            );
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_undo) {
            setAutoMode(AutoMode.OFF);
            ctrl.undoMove();
        } else if (id == R.id.action_redo) {
            setAutoMode(AutoMode.OFF);
            ctrl.redoMove();
        }

        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_new_game) {
            gameDialog();
        } else if (id == R.id.nav_edit_board) {
            startEditBoard(ctrl.getFEN());
        } else if (id == R.id.nav_flip_board) {
            boardFlipped = !cb.flipped;
            cb.setFlipped(boardFlipped);
        } else if (id == R.id.nav_load_pgn) {
            selectPgnFileDialog();
        } else if (id == R.id.nav_resign) {
            if (ctrl.humansTurn()) {
                //removeDialog(CONFIRM_RESIGN_DIALOG);
                //showDialog(CONFIRM_RESIGN_DIALOG);
                resignDialog();
            }
        } else if (id == R.id.nav_force_computer) {
            ctrl.stopSearch();
        } else if (id == R.id.nav_open_book) {
            if (storageAvailable()) {
                selectBookDialog();
            }
        } else if (id == R.id.nav_theme) {
            setColorThemeDialog();
        } else if (id == R.id.nav_about) {
            aboutDialog();
        } else if (id == R.id.nav_engine) {
            if (storageAvailable()) {
                manageEnginesDialog();
            } else {
                selectEngineDialog(true);
            }
        } else if (id == R.id.nav_settings) {
            Intent i = new Intent(MDChess.this, Preferences.class);
            startActivityForResult(i, RESULT_SETTINGS);
        } else if (id == R.id.nav_file_pgn) {
            fileMenuDialog();
        } else if (id == R.id.nav_game_mode) {
            gameModeDialog();
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.main_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    protected void onResume() {
        lastVisibleMillis = 0;
        if (ctrl != null) {
            ctrl.setGuiPaused(false);
        }
        notificationActive = true;
        updateNotification();
        super.onResume();
    }

    @Override
    protected void onPause() {
        if (ctrl != null) {
            setAutoMode(AutoMode.OFF);
            ctrl.setGuiPaused(true);
            byte[] data = ctrl.toByteArray();
            Editor editor = settings.edit();
            String dataStr = byteArrToString(data);
            editor.putString("gameState", dataStr);
            editor.putInt("gameStateVersion", 3);
            editor.apply();
        }
        lastVisibleMillis = System.currentTimeMillis();
        updateNotification();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        setAutoMode(AutoMode.OFF);
        if (ctrl != null) {
            ctrl.shutdownEngine();
        }
        setNotification(false);
        super.onDestroy();
    }

    private int getIntSetting(String settingName, int defaultValue) {
        String tmp = settings.getString(settingName, String.format(Locale.US, "%d", defaultValue));
        return Integer.parseInt(tmp);
    }

    private void readPrefs() {
        int modeNr = getIntSetting("gameMode", 1);
        gameMode = new GameMode(modeNr);
        String oldPlayerName = playerName;
        playerName = settings.getString("playerName", "Player");
        boardFlipped = settings.getBoolean("boardFlipped", false);
        autoSwapSides = settings.getBoolean("autoSwapSides", false);
        playerNameFlip = settings.getBoolean("playerNameFlip", true);
        setBoardFlip(!playerName.equals(oldPlayerName));
        boolean drawSquareLabels = settings.getBoolean("drawSquareLabels", false);
        cb.setDrawSquareLabels(drawSquareLabels);
        cb.oneTouchMoves = settings.getBoolean("oneTouchMoves", false);
        cb.toggleSelection = getIntSetting("squareSelectType", 0) == 1;
        cb.highlightLastMove = settings.getBoolean("highlightLastMove", true);
        cb.setBlindMode(settings.getBoolean("blindMode", false));

        mShowThinking = settings.getBoolean("showThinking", false);
        mShowStats = settings.getBoolean("showStats", true);
        numPV = settings.getInt("numPV", 1);
        ctrl.setMultiPVMode(numPV);
        mWhiteBasedScores = settings.getBoolean("whiteBasedScores", false);
        maxNumArrows = getIntSetting("thinkingArrows", 2);
        mShowBookHints = settings.getBoolean("bookHints", false);

        String engine = settings.getString("engine", "cuckoochess");
        int strength = settings.getInt("strength", 1000);
        setEngineStrength(engine, strength);

        mPonderMode = settings.getBoolean("ponderMode", false);
        if (!mPonderMode) {
            ctrl.stopPonder();
        }

        timeControl = getIntSetting("timeControl", 120000);
        movesPerSession = getIntSetting("movesPerSession", 60);
        timeIncrement = getIntSetting("timeIncrement", 0);

        autoMoveDelay = getIntSetting("autoDelay", 5000);

        scrollSensitivity = Float.parseFloat(settings.getString("scrollSensitivity", "2"));
        invertScrollDirection = settings.getBoolean("invertScrollDirection", false);
        discardVariations = settings.getBoolean("discardVariations", false);
        Util.setFullScreenMode(this, settings);
        boolean useWakeLock = settings.getBoolean("wakeLock", false);
        setWakeLock(useWakeLock);

        int fontSize = getIntSetting("fontSize", 12);
        int statusFontSize = fontSize;
        Configuration config = getResources().getConfiguration();
        if (config.orientation == Configuration.ORIENTATION_PORTRAIT) {
            statusFontSize = Math.min(statusFontSize, 16);
        }
        status.setTextSize(statusFontSize);
        soundEnabled = settings.getBoolean("soundEnabled", false);
        vibrateEnabled = settings.getBoolean("vibrateEnabled", false);
        animateMoves = settings.getBoolean("animateMoves", true);
        autoScrollTitle = settings.getBoolean("autoScrollTitle", true);
        setTitleScrolling();

        //guideShowOnStart = settings.getBoolean("guideShowOnStart", true);

        bookOptions.filename = settings.getString("bookFile", "");
        bookOptions.maxLength = getIntSetting("bookMaxLength", 1000000);
        bookOptions.preferMainLines = settings.getBoolean("bookPreferMainLines", false);
        bookOptions.tournamentMode = settings.getBoolean("bookTournamentMode", false);
        bookOptions.random = (settings.getInt("bookRandom", 500) - 500) * (3.0 / 500);
        setBookOptions();

        File extDir = Environment.getExternalStorageDirectory();
        String sep = File.separator;
        engineOptions.hashMB = getIntSetting("hashMB", 16);
        engineOptions.unSafeHash = new File(
                extDir + sep + engineDir + sep + ".unsafehash").exists();
        engineOptions.hints = settings.getBoolean("tbHints", false);
        engineOptions.hintsEdit = settings.getBoolean("tbHintsEdit", false);
        engineOptions.rootProbe = settings.getBoolean("tbRootProbe", true);
        engineOptions.engineProbe = settings.getBoolean("tbEngineProbe", true);

        String gtbPath = settings.getString("gtbPath", "").trim();
        if (gtbPath.length() == 0) {
            gtbPath = extDir.getAbsolutePath() + sep + gtbDefaultDir;
        }
        engineOptions.gtbPath = gtbPath;
        engineOptions.gtbPathNet = settings.getString("gtbPathNet", "").trim();
        String rtbPath = settings.getString("rtbPath", "").trim();
        if (rtbPath.length() == 0) {
            rtbPath = extDir.getAbsolutePath() + sep + rtbDefaultDir;
        }
        engineOptions.rtbPath = rtbPath;
        engineOptions.rtbPathNet = settings.getString("rtbPathNet", "").trim();

        setEngineOptions(false);
        setEgtbHints(cb.getSelectedSquare());

        updateThinkingInfo();

        pgnOptions.view.variations = settings.getBoolean("viewVariations", true);
        pgnOptions.view.comments = settings.getBoolean("viewComments", true);
        pgnOptions.view.nag = settings.getBoolean("viewNAG", true);
        pgnOptions.view.headers = settings.getBoolean("viewHeaders", false);
        final int oldViewPieceType = pgnOptions.view.pieceType;
        pgnOptions.view.pieceType = getIntSetting("viewPieceType", PGNOptions.PT_LOCAL);
        showVariationLine = settings.getBoolean("showVariationLine", false);
        pgnOptions.imp.variations = settings.getBoolean("importVariations", true);
        pgnOptions.imp.comments = settings.getBoolean("importComments", true);
        pgnOptions.imp.nag = settings.getBoolean("importNAG", true);
        pgnOptions.exp.variations = settings.getBoolean("exportVariations", true);
        pgnOptions.exp.comments = settings.getBoolean("exportComments", true);
        pgnOptions.exp.nag = settings.getBoolean("exportNAG", true);
        pgnOptions.exp.playerAction = settings.getBoolean("exportPlayerAction", false);
        pgnOptions.exp.clockInfo = settings.getBoolean("exportTime", false);

        ColorTheme.instance().readColors(settings);
        cb.setColors();


        gameTextListener.clear();
        setPieceNames(pgnOptions.view.pieceType);
        ctrl.prefsChanged(oldViewPieceType != pgnOptions.view.pieceType);
        // update the typeset in case of a change anyway, cause it could occur
        // as well in rotation
        setFigurineNotation(pgnOptions.view.pieceType == PGNOptions.PT_FIGURINE, fontSize);

    }

    /**
     * Change the Pieces into figurine or regular (i.e. letters) display
     */
    private void setFigurineNotation(boolean displayAsFigures, int fontSize) {
        if (displayAsFigures) {
            // increase the font cause it has different kerning and looks small
            float increaseFontSize = fontSize * 1.1f;
            moveList.setTypeface(figNotation, increaseFontSize);
            thinking.setTypeface(figNotation);
            thinking.setTextSize(increaseFontSize);
        } else {
            moveList.setTypeface(null, fontSize);
            thinking.setTypeface(defaultThinkingListTypeFace);
            thinking.setTextSize(fontSize);
        }
    }

    /**
     * Enable/disable title bar scrolling.
     */
    private void setTitleScrolling() {
        TextUtils.TruncateAt where = autoScrollTitle ? TextUtils.TruncateAt.MARQUEE
                : TextUtils.TruncateAt.END;
        whiteTitleText.setEllipsize(where);
        blackTitleText.setEllipsize(where);
    }

    @SuppressLint("Wakelock")
    private synchronized void setWakeLock(boolean enableLock) {
        if (enableLock) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private void setEngineStrength(String engine, int strength) {
        if (!storageAvailable()) {
            if (!"stockfish".equals(engine) && !"cuckoochess".equals(engine)) {
                engine = "stockfish";
            }
        }
        ctrl.setEngineStrength(engine, strength);
        setEngineTitle(engine, strength);
    }

    private void setEngineTitle(String engine, int strength) {
        String eName = "";
        if (EngineUtil.isOpenExchangeEngine(engine)) {
            String engineFileName = new File(engine).getName();
            ChessEngineResolver resolver = new ChessEngineResolver(this);
            List<ChessEngine> engines = resolver.resolveEngines();
            for (ChessEngine ce : engines) {
                if (EngineUtil.openExchangeFileName(ce).equals(engineFileName)) {
                    eName = ce.getName();
                    break;
                }
            }
        } else if (engine.contains("/")) {
            int idx = engine.lastIndexOf('/');
            eName = engine.substring(idx + 1);
        } else {
            eName = getString(engine.equals("cuckoochess") ?
                    R.string.cuckoochess_engine :
                    R.string.stockfish_engine);
            boolean analysis = (ctrl != null) && ctrl.analysisMode();
            if ((strength < 1000) && !analysis) {
                eName = String.format(Locale.US, "%s: %d%%", eName, strength / 10);
            }
        }
        engineTitleText.setText(eName);
    }

    /**
     * Update center field in second header line.
     */
    public final void updateTimeControlTitle() {
        /*int[] tmpInfo = ctrl.getTimeLimit();
        //StringBuilder sb = new StringBuilder();
        int tc = tmpInfo[0];
        int mps = tmpInfo[1];
        int inc = tmpInfo[2];
        /*if (mps > 0) {
            sb.append(mps);
            sb.append("/");
        }
        sb.append(timeToString(tc));
        if ((inc > 0) || (mps <= 0)) {
            sb.append("+");
            sb.append(tmpInfo[2] / 1000);
        }*/
        //status.setText(sb.toString());
    }

    @Override
    public void updateEngineTitle() {
        String engine = settings.getString("engine", "stockfish");
        int strength = settings.getInt("strength", 1000);
        setEngineTitle(engine, strength);
    }

    @Override
    public void updateMaterialDifferenceTitle(Util.MaterialDiff diff) {
        whiteFigText.setText(diff.white);
        blackFigText.setText(diff.black);
    }

    private void setBookOptions() {
        BookOptions options = new BookOptions(bookOptions);
        if (options.filename.length() > 0) {
            String sep = File.separator;
            if (!options.filename.startsWith(sep)) {
                File extDir = Environment.getExternalStorageDirectory();
                options.filename =
                        extDir.getAbsolutePath() + sep + bookDir + sep + options.filename;
            }
        }
        ctrl.setBookOptions(options);
    }

    private void setEngineOptions(boolean restart) {
        computeNetEngineID();
        ctrl.setEngineOptions(new EngineOptions(engineOptions), restart);
        Probe.getInstance().setPath(engineOptions.gtbPath, engineOptions.rtbPath,
                egtbForceReload);
        egtbForceReload = false;
    }

    private void computeNetEngineID() {
        String id = "";
        try {
            String engine = settings.getString("engine", "stockfish");
            if (EngineUtil.isNetEngine(engine)) {
                String[] lines = Util.readFile(engine);
                if (lines.length >= 3) {
                    id = lines[1] + ":" + lines[2];
                }
            }
        } catch (IOException e) {
            Log.d("Exception", e.toString());
        }
        engineOptions.networkID = id;
    }

    private void setEgtbHints(int sq) {
        if (!engineOptions.hints || (sq < 0)) {
            cb.setSquareDecorations(null);
            return;
        }

        Probe gtbProbe = Probe.getInstance();
        ArrayList<Pair<Integer, ProbeResult>> x = gtbProbe.movePieceProbe(cb.pos, sq);
        if (x == null) {
            cb.setSquareDecorations(null);
            return;
        }

        ArrayList<SquareDecoration> sd = new ArrayList<>();
        for (Pair<Integer, ProbeResult> p : x) {
            sd.add(new SquareDecoration(p.first, p.second));
        }
        cb.setSquareDecorations(sd);
    }

    private void startEditBoard(String fen) {
        Intent i = new Intent(MDChess.this, EditBoard.class);
        i.setAction(fen);
        startActivityForResult(i, RESULT_EDITBOARD);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case RESULT_SETTINGS:
                handlePrefsChange();
                break;
            case RESULT_EDITBOARD:
                if (resultCode == RESULT_OK) {
                    try {
                        String fen = data.getAction();
                        ctrl.setFENOrPGN(fen);
                        setBoardFlip(false);
                    } catch (ChessParseError e) {
                        Log.d("Exception", e.toString());
                    }
                }
                break;
            case RESULT_LOAD_PGN:
                if (resultCode == RESULT_OK) {
                    try {
                        String pgn = data.getAction();
                        int modeNr = ctrl.getGameMode().getModeNr();
                        if ((modeNr != GameMode.ANALYSIS) && (modeNr != GameMode.EDIT_GAME)) {
                            newGameMode();
                        }
                        ctrl.setFENOrPGN(pgn);
                        setBoardFlip(true);
                    } catch (ChessParseError e) {
                        Toast.makeText(getApplicationContext(), getParseErrString(e),
                                Toast.LENGTH_SHORT).show();
                    }
                }
                break;
            case RESULT_SELECT_SCID:
                if (resultCode == RESULT_OK) {
                    String pathName = data.getAction();
                    if (pathName != null) {
                        Editor editor = settings.edit();
                        editor.putString("currentScidFile", pathName);
                        editor.putInt("currFT", FT_SCID);
                        editor.apply();
                        Intent i = new Intent(MDChess.this, LoadScid.class);
                        i.setAction("org.mdc.chess.loadScid");
                        i.putExtra("org.mdc.chess.pathname", pathName);
                        startActivityForResult(i, RESULT_LOAD_PGN);
                    }
                }
                break;
            case RESULT_OI_PGN_LOAD:
                if (resultCode == RESULT_OK) {
                    String pathName = getFilePathFromUri(data.getData());
                    if (pathName != null) {
                        loadPGNFromFile(pathName);
                    }
                }
                break;
            case RESULT_OI_PGN_SAVE:
                if (resultCode == RESULT_OK) {
                    String pathName = getFilePathFromUri(data.getData());
                    if (pathName != null) {
                        if ((pathName.length() > 0) && !pathName.contains(".")) {
                            pathName += ".pgn";
                        }
                        savePGNToFile(pathName);
                    }
                }
                break;
            case RESULT_OI_FEN_LOAD:
                if (resultCode == RESULT_OK) {
                    String pathName = getFilePathFromUri(data.getData());
                    if (pathName != null) {
                        loadFENFromFile(pathName);
                    }
                }
                break;
            case RESULT_GET_FEN:
                if (resultCode == RESULT_OK) {
                    String fen = data.getStringExtra(Intent.EXTRA_TEXT);
                    if (fen == null) {
                        String pathName = getFilePathFromUri(data.getData());
                        loadFENFromFile(pathName);
                    }
                    setFenHelper(fen);
                }
                break;
            case RESULT_LOAD_FEN:
                if (resultCode == RESULT_OK) {
                    String fen = data.getAction();
                    setFenHelper(fen);
                }
                break;
            case RESULT_EDITOPTIONS:
                if (resultCode == RESULT_OK) {
                    @SuppressWarnings("unchecked")
                    Map<String, String> uciOpts =
                            (Map<String, String>) data.getSerializableExtra(
                                    "org.mdc.chess.ucioptions");
                    ctrl.setEngineUCIOptions(uciOpts);
                }
                break;
        }
    }

    /**
     * Set new game mode.
     */
    private void newGameMode() {
        Editor editor = settings.edit();
        String gameModeStr = String.format(Locale.US, "%d", GameMode.EDIT_GAME);
        editor.putString("gameMode", gameModeStr);
        editor.apply();
        gameMode = new GameMode(GameMode.EDIT_GAME);
        maybeAutoModeOff(gameMode);
        ctrl.setGameMode(gameMode);
    }

    private String getParseErrString(ChessParseError e) {
        if (e.resourceId == -1) {
            return e.getMessage();
        } else {
            return getString(e.resourceId);
        }
    }

    private int nameMatchScore(String name, String match) {
        if (name == null) {
            return 0;
        }
        String lName = name.toLowerCase(Locale.US);
        String lMatch = match.toLowerCase(Locale.US);
        if (name.equals(match)) {
            return 6;
        }
        if (lName.equals(lMatch)) {
            return 5;
        }
        if (name.startsWith(match)) {
            return 4;
        }
        if (lName.startsWith(lMatch)) {
            return 3;
        }
        if (name.contains(match)) {
            return 2;
        }
        if (lName.contains(lMatch)) {
            return 1;
        }
        return 0;
    }

    private void setBoardFlip() {
        setBoardFlip(false);
    }

    /**
     * Set a boolean preference setting.
     */
    private void setBooleanPref(boolean value) {
        Editor editor = settings.edit();
        editor.putBoolean("boardFlipped", value);
        editor.apply();
    }

    private void setBoardFlip(boolean matchPlayerNames) {
        boolean flipped = boardFlipped;
        if (playerNameFlip && matchPlayerNames && (ctrl != null)) {
            final TreeMap<String, String> headers = new TreeMap<>();
            ctrl.getHeaders(headers);
            int whiteMatch = nameMatchScore(headers.get("White"), playerName);
            int blackMatch = nameMatchScore(headers.get("Black"), playerName);
            if ((flipped && (whiteMatch > blackMatch)) ||
                    (!flipped && (whiteMatch < blackMatch))) {
                flipped = !flipped;
                boardFlipped = flipped;
                setBooleanPref(flipped);
            }
        }
        if (autoSwapSides) {
            if (gameMode.analysisMode()) {
                flipped = !cb.pos.whiteMove;
            } else if (gameMode.playerWhite() && gameMode.playerBlack()) {
                flipped = !cb.pos.whiteMove;
            } else {
                flipped = !gameMode.playerWhite() && (gameMode.playerBlack() || !cb.pos.whiteMove);
            }
// two computers


        }
        cb.setFlipped(flipped);
    }

    @Override
    public void setSelection(int sq) {
        cb.setSelection(cb.highlightLastMove ? sq : -1);
        cb.userSelectedSquare = false;
        setEgtbHints(sq);
    }

    @Override
    public void setStatus(GameStatus s) {
        String str;
        switch (s.state) {
            case ALIVE:
                str = Integer.valueOf(s.moveNr).toString();
                if (s.white) {
                    str += ". " + getString(R.string.whites_move);
                } else {
                    str += "... " + getString(R.string.blacks_move);
                }
                if (s.ponder) str += " (" + getString(R.string.ponder) + ")";
                if (s.thinking) str += " (" + getString(R.string.thinking) + ")";
                if (s.analyzing) str += " (" + getString(R.string.analyzing) + ")";
                break;
            case WHITE_MATE:
                str = getString(R.string.white_mate);
                break;
            case BLACK_MATE:
                str = getString(R.string.black_mate);
                break;
            case WHITE_STALEMATE:
            case BLACK_STALEMATE:
                str = getString(R.string.stalemate);
                break;
            case DRAW_REP: {
                str = getString(R.string.draw_rep);
                if (s.drawInfo.length() > 0) {
                    str = str + " [" + s.drawInfo + "]";
                }
                break;
            }
            case DRAW_50: {
                str = getString(R.string.draw_50);
                if (s.drawInfo.length() > 0) {
                    str = str + " [" + s.drawInfo + "]";
                }
                break;
            }
            case DRAW_NO_MATE:
                str = getString(R.string.draw_no_mate);
                break;
            case DRAW_AGREE:
                str = getString(R.string.draw_agree);
                break;
            case RESIGN_WHITE:
                str = getString(R.string.resign_white);
                break;
            case RESIGN_BLACK:
                str = getString(R.string.resign_black);
                break;
            default:
                throw new RuntimeException();
        }
        setStatusString(str);
    }

    private void setStatusString(String str) {
        status.setText(str);
    }

    @Override
    public void moveListUpdated() {
        moveList.setText(gameTextListener.getText());
        int currPos = gameTextListener.getCurrPos();
        int line = moveList.getLineForOffset(currPos);
        if (line >= 0) {
            int y = (line - 1) * moveList.getLineHeight();
            moveListScroll.scrollTo(0, y);
        }
    }

    @Override
    public boolean whiteBasedScores() {
        return mWhiteBasedScores;
    }

    @Override
    public boolean ponderMode() {
        return mPonderMode;
    }

    @Override
    public Context getContext() {
        return this;
    }

    @Override
    public String playerName() {
        return playerName;
    }

    @Override
    public boolean discardVariations() {
        return discardVariations;
    }

    /**
     * Report a move made that is a candidate for GUI animation.
     */
    public void setAnimMove(Position sourcePos, Move move, boolean forward) {
        if (animateMoves && (move != null)) {
            cb.setAnimMove(sourcePos, move, forward);
        }
    }

    @Override
    public void setPosition(Position pos, String variantInfo, ArrayList<Move> variantMoves) {
        variantStr = variantInfo;
        this.variantMoves = variantMoves;
        cb.setPosition(pos);
        setBoardFlip();
        updateThinkingInfo();
        setEgtbHints(cb.getSelectedSquare());
    }

    @Override
    public void setThinkingInfo(ThinkingInfo ti) {
        thinkingStr1 = ti.pvStr;
        thinkingStr2 = ti.statStr;
        bookInfoStr = ti.bookInfo;
        this.pvMoves = ti.pvMoves;
        this.bookMoves = ti.bookMoves;
        updateThinkingInfo();

        if (ctrl.computerBusy()) {
            lastComputationMillis = System.currentTimeMillis();
        } else {
            lastComputationMillis = 0;
        }
        updateNotification();
    }

    private void updateThinkingInfo() {
        boolean thinkingEmpty = true;
        {
            String s = "";
            if (mShowThinking || gameMode.analysisMode()) {
                s = thinkingStr1;
                if (s.length() > 0) thinkingEmpty = false;
                if (mShowStats) {
                    if (!thinkingEmpty) {
                        s += "\n";
                    }
                    s += thinkingStr2;
                    if (s.length() > 0) thinkingEmpty = false;
                }
            }
            thinking.setText(s, TextView.BufferType.SPANNABLE);
        }
        if (mShowBookHints && (bookInfoStr.length() > 0)) {
            String s = "";
            if (!thinkingEmpty) {
                s += "<br>";
            }
            s += Util.boldStart + getString(R.string.book) + Util.boldStop + bookInfoStr;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                thinking.append(Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY));
            } else {
                //noinspection deprecation
                thinking.append(Html.fromHtml(s));
            }

            thinkingEmpty = false;
        }
        if (showVariationLine && (variantStr.indexOf(' ') >= 0)) {
            String s = "";
            if (!thinkingEmpty) {
                s += "<br>";
            }
            s += Util.boldStart + getString(R.string.variation) + Util.boldStop + variantStr;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                thinking.append(Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY));
            } else {
                //noinspection deprecation
                thinking.append(Html.fromHtml(s));
            }
            thinkingEmpty = false;
        }
        thinking.setVisibility(thinkingEmpty ? View.GONE : View.VISIBLE);

        List<Move> hints = null;
        if (mShowThinking || gameMode.analysisMode()) {
            ArrayList<ArrayList<Move>> pvMovesTmp = pvMoves;
            if (pvMovesTmp.size() == 1) {
                hints = pvMovesTmp.get(0);
            } else if (pvMovesTmp.size() > 1) {
                hints = new ArrayList<>();
                for (ArrayList<Move> pv : pvMovesTmp) {
                    if (!pv.isEmpty()) {
                        hints.add(pv.get(0));
                    }
                }
            }
        }
        if ((hints == null) && mShowBookHints) {
            hints = bookMoves;
        }
        if (((hints == null) || hints.isEmpty()) &&
                (variantMoves != null) && variantMoves.size() > 1) {
            hints = variantMoves;
        }
        if ((hints != null) && (hints.size() > maxNumArrows)) {
            hints = hints.subList(0, maxNumArrows);
        }
        cb.setMoveHints(hints);
    }

    private void gameDialog() {
        new MaterialDialog.Builder(this)
                .title(R.string.option_new_game)
                .content(R.string.start_new_game)
                .positiveText(R.string.black)
                .negativeText(R.string.white)
                .neutralText(R.string.yes)
                .onPositive(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog,
                            @NonNull DialogAction which) {
                        startNewGame(1);
                    }
                })
                .onNeutral(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog,
                            @NonNull DialogAction which) {
                        startNewGame(2);
                    }
                })
                .onNegative(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog,
                            @NonNull DialogAction which) {
                        startNewGame(0);
                    }
                })
                .show();
    }

    private void resignDialog() {
        new MaterialDialog.Builder(this)
                .title(R.string.option_resign_game)
                .positiveText(R.string.yes)
                .negativeText(R.string.no)
                .neutralText(R.string.option_new_game)
                .onPositive(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog,
                            @NonNull DialogAction which) {
                        if (ctrl.humansTurn()) {
                            ctrl.resignGame();
                        }
                    }
                })
                .onNeutral(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog,
                            @NonNull DialogAction which) {
                        gameDialog();
                    }
                })
                .onNegative(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog,
                            @NonNull DialogAction which) {

                    }
                })
                .show();
    }

    private void startNewGame(int type) {
        if (type != 2) {
            int gameModeType = (type == 0) ? GameMode.PLAYER_WHITE : GameMode.PLAYER_BLACK;
            Editor editor = settings.edit();
            String gameModeStr = String.format(Locale.US, "%d", gameModeType);
            editor.putString("gameMode", gameModeStr);
            editor.apply();
            gameMode = new GameMode(gameModeType);
        }
//        savePGNToFile(".autosave.pgn", true);
        TimeControlData tcData = new TimeControlData();
        tcData.setTimeControl(timeControl, movesPerSession, timeIncrement);
        ctrl.newGame(gameMode, tcData);
        ctrl.startGame();
        setBoardFlip(true);
        updateEngineTitle();
    }

    private void promoteDialog() {
        final CharSequence[] items = {
                getString(R.string.queen), getString(R.string.rook),
                getString(R.string.bishop), getString(R.string.knight)
        };
        new MaterialDialog.Builder(this)
                .title(R.string.promote_pawn_to)
                .items(items)
                .itemsCallback(new MaterialDialog.ListCallback() {
                    @Override
                    public void onSelection(MaterialDialog dialog, View view, int which,
                            CharSequence text) {
                        ctrl.reportPromotePiece(which);
                    }
                })
                .show();
    }

    private void clipBoardDialog() {
        final int COPY_GAME = 0;
        final int COPY_POSITION = 1;
        final int PASTE = 2;

        setAutoMode(AutoMode.OFF);
        List<CharSequence> lst = new ArrayList<>();
        List<Integer> actions = new ArrayList<>();
        lst.add(getString(R.string.copy_game));
        actions.add(COPY_GAME);
        lst.add(getString(R.string.copy_position));
        actions.add(COPY_POSITION);
        lst.add(getString(R.string.paste));
        actions.add(PASTE);
        final List<Integer> finalActions = actions;
        new MaterialDialog.Builder(this)
                .title(R.string.tools_menu)
                .items(lst)
                .itemsCallback(new MaterialDialog.ListCallback() {
                    @Override
                    public void onSelection(MaterialDialog dialog, View view, int which,
                            CharSequence text) {
                        switch (finalActions.get(which)) {
                            case COPY_GAME: {
                                String pgn = ctrl.getPGN();
                                ClipboardManager clipboard = (ClipboardManager) getSystemService(
                                        CLIPBOARD_SERVICE);
                                clipboard.setPrimaryClip(new ClipData("MD Chess game",
                                        new String[]{"application/x-chess-pgn",
                                                ClipDescription.MIMETYPE_TEXT_PLAIN},
                                        new ClipData.Item(pgn)));
                                break;
                            }
                            case COPY_POSITION: {
                                String fen = ctrl.getFEN() + "\n";
                                ClipboardManager clipboard = (ClipboardManager) getSystemService(
                                        CLIPBOARD_SERVICE);
                                clipboard.setPrimaryClip(new ClipData(fen,
                                        new String[]{"application/x-chess-fen",
                                                ClipDescription.MIMETYPE_TEXT_PLAIN},
                                        new ClipData.Item(fen)));
                                break;
                            }
                            case PASTE: {
                                ClipboardManager clipboard = (ClipboardManager) getSystemService(
                                        CLIPBOARD_SERVICE);
                                if (clipboard.hasPrimaryClip()) {
                                    ClipData clip = clipboard.getPrimaryClip();
                                    StringBuilder fenPgn = new StringBuilder();
                                    for (int i = 0; i < clip.getItemCount(); i++) {
                                        fenPgn.append(clip.getItemAt(i).coerceToText(
                                                getApplicationContext()));
                                    }
                                    try {
                                        ctrl.setFENOrPGN(fenPgn.toString());
                                        setBoardFlip(true);
                                    } catch (ChessParseError e) {
                                        Toast.makeText(getApplicationContext(),
                                                getParseErrString(e), Toast.LENGTH_SHORT).show();
                                    }
                                }
                                break;
                            }
                        }
                    }
                })
                .show();
    }

    private void boardMenuDialog() {
        final int CLIPBOARD = 0;
        final int FILEMENU = 1;
        final int SHARE = 2;
        final int GET_FEN = 3;

        setAutoMode(AutoMode.OFF);
        List<CharSequence> lst = new ArrayList<>();
        List<Integer> actions = new ArrayList<>();
        lst.add(getString(R.string.clipboard));
        actions.add(CLIPBOARD);
        if (storageAvailable()) {
            lst.add(getString(R.string.option_file));
            actions.add(FILEMENU);
        }
        lst.add(getString(R.string.share));
        actions.add(SHARE);
        if (hasFenProvider(getPackageManager())) {
            lst.add(getString(R.string.get_fen));
            actions.add(GET_FEN);
        }
        final List<Integer> finalActions = actions;
        new MaterialDialog.Builder(this)
                .title(R.string.tools_menu)
                .items(lst)
                .itemsCallback(new MaterialDialog.ListCallback() {
                    @Override
                    public void onSelection(MaterialDialog dialog, View view, int which,
                            CharSequence text) {
                        switch (finalActions.get(which)) {
                            case CLIPBOARD: {
                                clipBoardDialog();
                                break;
                            }
                            case FILEMENU: {
                                fileMenuDialog();
                                break;
                            }
                            case SHARE: {
                                shareGame();
                                break;
                            }
                            case GET_FEN:
                                getFen();
                                break;
                        }
                    }
                })
                .show();
    }

    private void shareGame() {
        Intent i = new Intent(Intent.ACTION_SEND);
        //noinspection deprecation
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, ctrl.getPGN());
        try {
            startActivity(Intent.createChooser(i, getString(R.string.share_pgn_game)));
        } catch (ActivityNotFoundException ex) {
            // Ignore
        }
    }

    private void fileMenuDialog() {
        final int LOAD_LAST_FILE = 0;
        final int LOAD_GAME = 1;
        final int LOAD_POS = 2;
        final int LOAD_SCID_GAME = 3;
        final int SAVE_GAME = 4;

        setAutoMode(AutoMode.OFF);
        List<CharSequence> lst = new ArrayList<>();
        List<Integer> actions = new ArrayList<>();
        if (currFileType() != FT_NONE) {
            lst.add(getString(R.string.load_last_file));
            actions.add(LOAD_LAST_FILE);
        }
        lst.add(getString(R.string.load_game));
        actions.add(LOAD_GAME);
        lst.add(getString(R.string.load_position));
        actions.add(LOAD_POS);
        if (hasScidProvider()) {
            lst.add(getString(R.string.load_scid_game));
            actions.add(LOAD_SCID_GAME);
        }
        lst.add(getString(R.string.save_game));
        actions.add(SAVE_GAME);
        final List<Integer> finalActions = actions;
        new MaterialDialog.Builder(this)
                .title(R.string.load_save_menu)
                .items(lst)
                .itemsCallback(new MaterialDialog.ListCallback() {
                    @Override
                    public void onSelection(MaterialDialog dialog, View view, int which,
                            CharSequence text) {
                        switch (finalActions.get(which)) {
                            case LOAD_LAST_FILE:
                                loadLastFile();
                                break;
                            case LOAD_GAME:
                                selectFile(R.string.select_pgn_file, R.string.pgn_load,
                                        "currentPGNFile", pgnDir,
                                        SELECT_PGN_FILE_DIALOG, RESULT_OI_PGN_LOAD);
                                break;
                            case SAVE_GAME:
                                selectFile(R.string.select_pgn_file_save, R.string.pgn_save,
                                        "currentPGNFile", pgnDir,
                                        SELECT_PGN_FILE_SAVE_DIALOG, RESULT_OI_PGN_SAVE);
                                break;
                            case LOAD_POS:
                                selectFile(R.string.select_fen_file, R.string.pgn_load,
                                        "currentFENFile", fenDir,
                                        SELECT_FEN_FILE_DIALOG, RESULT_OI_FEN_LOAD);
                                break;
                            case LOAD_SCID_GAME:
                                selectScidFile();
                                break;
                        }
                    }
                })
                .show();
    }

    /**
     * Open dialog to select a game/position from the last used file.
     */
    private void loadLastFile() {
        String path = currPathName();
        if (path.length() == 0) {
            return;
        }
        setAutoMode(AutoMode.OFF);
        switch (currFileType()) {
            case FT_PGN:
                loadPGNFromFile(path);
                break;
            case FT_SCID: {
                Intent data = new Intent(path);
                onActivityResult(RESULT_SELECT_SCID, RESULT_OK, data);
                break;
            }
            case FT_FEN:
                loadFENFromFile(path);
                break;
        }
    }

    private void aboutDialog() {
        //AlertDialog.Builder builder = new AlertDialog.Builder(this);
        String title = getString(R.string.app_name);
        WebView wv = new WebView(this);
        //builder.setView(wv);
        InputStream is = getResources().openRawResource(R.raw.about);
        String data = Util.readFromStream(is);
        if (data == null) {
            data = "";
        }
        try {
            is.close();
        } catch (IOException e1) {
            Log.d("Exception", e1.toString());
        }
        wv.loadDataWithBaseURL(null, data, "text/html", "utf-8", null);
        try {
            PackageInfo pi = getPackageManager().getPackageInfo("org.mdc.chess", 0);
            title += " " + pi.versionName;
        } catch (NameNotFoundException e) {
            Log.d("Exception", e.toString());
        }
        new MaterialDialog.Builder(this)
                .title(title)
                .customView(wv, true)
                .show();
    }

    private void selectBookDialog() {
        String[] fileNames = findFilesInDirectory(bookDir, new FileNameFilter() {
            @Override
            public boolean accept(String filename) {
                int dotIdx = filename.lastIndexOf(".");
                if (dotIdx < 0) {
                    return false;
                }
                String ext = filename.substring(dotIdx + 1);
                return (ext.equals("ctg") || ext.equals("bin"));
            }
        });
        final int numFiles = fileNames.length;
        CharSequence[] items = new CharSequence[numFiles + 1];
        System.arraycopy(fileNames, 0, items, 0, numFiles);
        items[numFiles] = getString(R.string.internal_book);
        final CharSequence[] finalItems = items;
        int defaultItem = numFiles;
        for (int i = 0; i < numFiles; i++) {
            if (bookOptions.filename.equals(items[i])) {
                defaultItem = i;
                break;
            }
        }
        new MaterialDialog.Builder(this)
                .title(R.string.select_opening_book_file)
                .items(items)
                .itemsCallbackSingleChoice(defaultItem,
                        new MaterialDialog.ListCallbackSingleChoice() {
                            @Override
                            public boolean onSelection(MaterialDialog dialog, View view, int which,
                                    CharSequence text) {
                                /**
                                 * If you use alwaysCallSingleChoiceCallback(), which is
                                 * discussed below,
                                 * returning false here won't allow the newly selected radio
                                 * button to actually be selected.
                                 **/
                                Editor editor = settings.edit();
                                String bookFile = "";
                                if (which < numFiles) {
                                    bookFile = finalItems[which].toString();
                                }
                                editor.putString("bookFile", bookFile);
                                editor.apply();
                                bookOptions.filename = bookFile;
                                setBookOptions();
                                dialog.dismiss();
                                return true;
                            }
                        })
                .show();
    }

    private void selectEngineDialog(final boolean abortOnCancel) {
        final ArrayList<String> items = new ArrayList<>();
        final ArrayList<String> ids = new ArrayList<>();
        ids.add("stockfish");
        items.add(getString(R.string.stockfish_engine));
        ids.add("cuckoochess");
        items.add(getString(R.string.cuckoochess_engine));

        if (storageAvailable()) {
            final String sep = File.separator;
            final String base = Environment.getExternalStorageDirectory() + sep + engineDir + sep;
            {
                ChessEngineResolver resolver = new ChessEngineResolver(this);
                List<ChessEngine> engines = resolver.resolveEngines();
                ArrayList<Pair<String, String>> oexEngines = new ArrayList<>();
                for (ChessEngine engine : engines) {
                    if ((engine.getName() != null) && (engine.getFileName() != null) &&
                            (engine.getPackageName() != null)) {
                        oexEngines.add(
                                new Pair<>(EngineUtil.openExchangeFileName(engine),
                                        engine.getName()));
                    }
                }
                Collections.sort(oexEngines, new Comparator<Pair<String, String>>() {
                    @Override
                    public int compare(Pair<String, String> lhs, Pair<String, String> rhs) {
                        return lhs.second.compareTo(rhs.second);
                    }
                });
                for (Pair<String, String> eng : oexEngines) {
                    ids.add(base + EngineUtil.openExchangeDir + sep + eng.first);
                    items.add(eng.second);
                }
            }

            String[] fileNames = findFilesInDirectory(engineDir, new FileNameFilter() {
                @Override
                public boolean accept(String filename) {
                    return !reservedEngineName(filename);
                }
            });
            for (String file : fileNames) {
                ids.add(base + file);
                items.add(file);
            }
        }

        String currEngine = ctrl.getEngine();
        int defaultItem = 0;
        final int nEngines = items.size();
        for (int i = 0; i < nEngines; i++) {
            if (ids.get(i).equals(currEngine)) {
                defaultItem = i;
                break;
            }
        }
        new MaterialDialog.Builder(this)
                .title(R.string.select_chess_engine)
                .items(items)
                .itemsCallbackSingleChoice(defaultItem,
                        new MaterialDialog.ListCallbackSingleChoice() {
                            @Override
                            public boolean onSelection(MaterialDialog dialog, View view, int which,
                                    CharSequence text) {
                                /**
                                 * If you use alwaysCallSingleChoiceCallback(), which is
                                 * discussed below,
                                 * returning false here won't allow the newly selected radio
                                 * button to actually be selected.
                                 **/
                                Editor editor = settings.edit();
                                String engine = ids.get(which);
                                editor.putString("engine", engine);
                                editor.apply();
                                dialog.dismiss();
                                int strength = settings.getInt("strength", 1000);
                                setEngineOptions(false);
                                setEngineStrength(engine, strength);
                                return true;
                            }
                        })
                .cancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialogInterface) {
                        if (!abortOnCancel) {
                            manageEnginesDialog();
                        }
                    }
                })
                .show();
    }

    private void selectPgnFileDialog() {
        selectFileDialog(pgnDir, R.string.select_pgn_file, R.string.no_pgn_files,
                "currentPGNFile", new Loader() {
                    @Override
                    public void load(String pathName) {
                        loadPGNFromFile(pathName);
                    }
                });
    }

    private void selectFenFileDialog() {
        selectFileDialog(fenDir, R.string.select_fen_file, R.string.no_fen_files,
                "currentFENFile", new Loader() {
                    @Override
                    public void load(String pathName) {
                        loadFENFromFile(pathName);
                    }
                });
    }

    private void selectFileDialog(final String defaultDir, int selectFileMsg,
            int noFilesMsg,
            String settingsName, final Loader loader) {
        setAutoMode(AutoMode.OFF);
        final String[] files = findFilesInDirectory(defaultDir, null);
        final int numFiles = files.length;
        if (numFiles == 0) {
            new MaterialDialog.Builder(this)
                    .title(R.string.app_name)
                    .content(noFilesMsg);
        } else {
            int defaultItem = 0;
            String currentFile = settings.getString(settingsName, "");
            currentFile = new File(currentFile).getName();
            for (int i = 0; i < numFiles; i++) {
                if (currentFile.equals(files[i])) {
                    defaultItem = i;
                    break;
                }
            }
            new MaterialDialog.Builder(this)
                    .title(selectFileMsg)
                    .items(files)
                    .itemsCallbackSingleChoice(defaultItem,
                            new MaterialDialog.ListCallbackSingleChoice() {
                                @Override
                                public boolean onSelection(MaterialDialog dialog, View view,
                                        int which, CharSequence text) {
                                    /**
                                     * If you use alwaysCallSingleChoiceCallback(), which is
                                     * discussed below,
                                     * returning false here won't allow the newly selected radio
                                     * button to actually be selected.
                                     **/
                                    String sep = File.separator;
                                    String fn = files[which];
                                    String pathName =
                                            Environment.getExternalStorageDirectory() + sep
                                                    + defaultDir
                                                    + sep + fn;
                                    loader.load(pathName);
                                    return true;
                                }
                            })
                    .show();
        }
    }

    private void selectPgnFileSaveDialog() {
        setAutoMode(AutoMode.OFF);
        final String[] fileNames = findFilesInDirectory(pgnDir, null);
        final int numFiles = fileNames.length;
        int defaultItem = 0;
        String currentPGNFile = settings.getString("currentPGNFile", "");
        currentPGNFile = new File(currentPGNFile).getName();
        for (int i = 0; i < numFiles; i++) {
            if (currentPGNFile.equals(fileNames[i])) {
                defaultItem = i;
                break;
            }
        }
        CharSequence[] items = new CharSequence[numFiles + 1];
        System.arraycopy(fileNames, 0, items, 0, numFiles);
        items[numFiles] = getString(R.string.new_file);
        new MaterialDialog.Builder(this)
                .title(R.string.select_pgn_file_save)
                .items(items)
                .itemsCallbackSingleChoice(defaultItem,
                        new MaterialDialog.ListCallbackSingleChoice() {
                            @Override
                            public boolean onSelection(MaterialDialog dialog, View view, int which,
                                    CharSequence text) {
                                /**
                                 * If you use alwaysCallSingleChoiceCallback(), which is
                                 * discussed below,
                                 * returning false here won't allow the newly selected radio
                                 * button to actually be selected.
                                 **/
                                String pgnFile;
                                if (which >= numFiles) {
                                    dialog.dismiss();
                                    selectPgnSaveNewFileDialog();
                                } else {
                                    dialog.dismiss();
                                    pgnFile = fileNames[which];
                                    String sep = File.separator;
                                    String pathName =
                                            Environment.getExternalStorageDirectory() + sep + pgnDir
                                                    + sep
                                                    + pgnFile;
                                    savePGNToFile(pathName);
                                }
                                return true;
                            }
                        })
                .show();
    }

    private void selectPgnSaveNewFileDialog() {
        setAutoMode(AutoMode.OFF);
        View content = View.inflate(this, R.layout.create_pgn_file, null);

        final EditText fileNameView = (EditText) content.findViewById(R.id.create_pgn_filename);
        final TextInputLayout fileNameWrapper = (TextInputLayout) content.findViewById(
                R.id.create_pgn_filename_wrapper);
        fileNameWrapper.setHint(content.getResources().getString(R.string.filename));
        fileNameView.setText("");
        final Runnable savePGN = new Runnable() {
            public void run() {
                String pgnFile = fileNameView.getText().toString();
                if ((pgnFile.length() > 0) && !pgnFile.contains(".")) {
                    pgnFile += ".pgn";
                }
                String sep = File.separator;
                String pathName =
                        Environment.getExternalStorageDirectory() + sep + pgnDir + sep + pgnFile;
                savePGNToFile(pathName);
            }
        };

        new MaterialDialog.Builder(this)
                .title(R.string.select_pgn_file_save)
                .customView(content, true)
                .positiveText(android.R.string.ok)
                .negativeText(R.string.cancel)
                .onPositive(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog,
                            @NonNull DialogAction which) {
                        savePGN.run();
                    }
                })
                .onNegative(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog,
                            @NonNull DialogAction which) {

                    }
                })
                .show();

        fileNameView.setOnKeyListener(new OnKeyListener() {
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if ((event.getAction() == KeyEvent.ACTION_DOWN) && (keyCode
                        == KeyEvent.KEYCODE_ENTER)) {
                    savePGN.run();
                    return true;
                }
                return false;
            }
        });
    }

    private void setColorThemeDialog() {
        String[] themes = new String[ColorTheme.themeNames.length];
        for (int i = 0; i < themes.length; i++) {
            themes[i] = getString(ColorTheme.themeNames[i]);
        }
        new MaterialDialog.Builder(this)
                .title(R.string.select_color_theme)
                .items(themes)
                .itemsCallbackSingleChoice(-1, new MaterialDialog.ListCallbackSingleChoice() {
                    @Override
                    public boolean onSelection(MaterialDialog dialog, View view, int which,
                            CharSequence text) {
                        /**
                         * If you use alwaysCallSingleChoiceCallback(), which is discussed below,
                         * returning false here won't allow the newly selected radio button to
                         * actually be selected.
                         **/
                        ColorTheme.instance().setTheme(settings, which);
                        cb.setColors();
                        gameTextListener.clear();
                        ctrl.prefsChanged(false);
                        dialog.dismiss();
                        return true;
                    }
                })
                .show();
    }

    private void gameModeDialog() {
        final CharSequence[] items = {
                getString(R.string.analysis_mode),
                getString(R.string.edit_replay_game),
                getString(R.string.play_white),
                getString(R.string.play_black),
                getString(R.string.two_players),
                getString(R.string.comp_vs_comp)
        };
        new MaterialDialog.Builder(this)
                .title(R.string.select_game_mode)
                .items(items)
                .itemsCallbackSingleChoice(-1, new MaterialDialog.ListCallbackSingleChoice() {
                    @Override
                    public boolean onSelection(MaterialDialog dialog, View view, int which,
                            CharSequence text) {
                        /**
                         * If you use alwaysCallSingleChoiceCallback(), which is discussed below,
                         * returning false here won't allow the newly selected radio button to
                         * actually be selected.
                         **/
                        int gameModeType = -1;
                        /* only flip site in case the player was specified resp. changed */
                        boolean flipSite = false;
                        switch (which) {
                            case 0:
                                gameModeType = GameMode.ANALYSIS;
                                break;
                            case 1:
                                gameModeType = GameMode.EDIT_GAME;
                                break;
                            case 2:
                                gameModeType = GameMode.PLAYER_WHITE;
                                flipSite = true;
                                break;
                            case 3:
                                gameModeType = GameMode.PLAYER_BLACK;
                                flipSite = true;
                                break;
                            case 4:
                                gameModeType = GameMode.TWO_PLAYERS;
                                break;
                            case 5:
                                gameModeType = GameMode.TWO_COMPUTERS;
                                break;
                            default:
                                break;
                        }
                        if (gameModeType >= 0) {
                            Editor editor = settings.edit();
                            String gameModeStr = String.format(Locale.US, "%d", gameModeType);
                            editor.putString("gameMode", gameModeStr);
                            editor.apply();
                            gameMode = new GameMode(gameModeType);
                            maybeAutoModeOff(gameMode);
                            ctrl.setGameMode(gameMode);
                            setBoardFlip(flipSite);
                        }
                        return true;
                    }
                })
                .show();
    }

    private void moveListMenuDialog() {
        final int EDIT_HEADERS = 0;
        final int EDIT_COMMENTS = 1;
        final int REMOVE_SUBTREE = 2;
        final int MOVE_VAR_UP = 3;
        final int MOVE_VAR_DOWN = 4;
        final int ADD_NULL_MOVE = 5;

        setAutoMode(AutoMode.OFF);
        List<CharSequence> lst = new ArrayList<>();
        List<Integer> actions = new ArrayList<>();
        lst.add(getString(R.string.edit_headers));
        actions.add(EDIT_HEADERS);
        if (ctrl.humansTurn()) {
            lst.add(getString(R.string.edit_comments));
            actions.add(EDIT_COMMENTS);
        }
        lst.add(getString(R.string.truncate_gametree));
        actions.add(REMOVE_SUBTREE);
        if (ctrl.canMoveVariationUp()) {
            lst.add(getString(R.string.move_var_up));
            actions.add(MOVE_VAR_UP);
        }
        if (ctrl.canMoveVariationDown()) {
            lst.add(getString(R.string.move_var_down));
            actions.add(MOVE_VAR_DOWN);
        }

        boolean allowNullMove =
                (gameMode.analysisMode() ||
                        (gameMode.playerWhite() && gameMode.playerBlack()
                                && !gameMode.clocksActive())) &&
                        !ctrl.inCheck();
        if (allowNullMove) {
            lst.add(getString(R.string.add_null_move));
            actions.add(ADD_NULL_MOVE);
        }
        final List<Integer> finalActions = actions;

        new MaterialDialog.Builder(this)
                .title(R.string.edit_game)
                .items(lst)
                .itemsCallback(new MaterialDialog.ListCallback() {
                    @Override
                    public void onSelection(MaterialDialog dialog, View view, int which,
                            CharSequence text) {
                        switch (finalActions.get(which)) {
                            case EDIT_HEADERS: {
                                final TreeMap<String, String> headers =
                                        new TreeMap<>();
                                ctrl.getHeaders(headers);

                                View content = View.inflate(MDChess.this,
                                        R.layout.edit_headers, null);

                                final TextView event, site, date, round, white, black;

                                event = (TextView) content.findViewById(R.id.ed_header_event);
                                site = (TextView) content.findViewById(R.id.ed_header_site);
                                date = (TextView) content.findViewById(R.id.ed_header_date);
                                round = (TextView) content.findViewById(R.id.ed_header_round);
                                white = (TextView) content.findViewById(R.id.ed_header_white);
                                black = (TextView) content.findViewById(R.id.ed_header_black);

                                event.setText(headers.get("Event"));
                                site.setText(headers.get("Site"));
                                date.setText(headers.get("Date"));
                                round.setText(headers.get("Round"));
                                white.setText(headers.get("White"));
                                black.setText(headers.get("Black"));

                                new MaterialDialog.Builder(MDChess.this)
                                        .title(R.string.edit_headers)
                                        .customView(content, true)
                                        .positiveText(android.R.string.ok)
                                        .negativeText(R.string.cancel)
                                        .onPositive(new MaterialDialog.SingleButtonCallback() {
                                            @Override
                                            public void onClick(@NonNull MaterialDialog dialog,
                                                    @NonNull DialogAction which) {
                                                headers.put("Event",
                                                        event.getText().toString().trim());
                                                headers.put("Site",
                                                        site.getText().toString().trim());
                                                headers.put("Date",
                                                        date.getText().toString().trim());
                                                headers.put("Round",
                                                        round.getText().toString().trim());
                                                headers.put("White",
                                                        white.getText().toString().trim());
                                                headers.put("Black",
                                                        black.getText().toString().trim());
                                                ctrl.setHeaders(headers);
                                                setBoardFlip(true);
                                            }
                                        })
                                        .onNegative(new MaterialDialog.SingleButtonCallback() {
                                            @Override
                                            public void onClick(@NonNull MaterialDialog dialog,
                                                    @NonNull DialogAction which) {

                                            }
                                        })
                                        .show();

                                break;
                            }
                            case EDIT_COMMENTS: {
                                View content = View.inflate(MDChess.this,
                                        R.layout.edit_comments, null);

                                MDChessController.CommentInfo commInfo = ctrl.getComments();

                                final TextView preComment, moveView, nag, postComment;
                                preComment = (TextView) content.findViewById(R.id.ed_comments_pre);
                                final TextInputLayout preCommentWrapper =
                                        (TextInputLayout) content.findViewById(
                                                R.id.ed_comments_pre_wrapper);
                                preCommentWrapper.setHint(
                                        content.getResources().getString(R.string.comment_before));
                                moveView = (TextView) content.findViewById(R.id.ed_comments_move);
                                nag = (TextView) content.findViewById(R.id.ed_comments_nag);
                                postComment = (TextView) content.findViewById(
                                        R.id.ed_comments_post);
                                final TextInputLayout postCommentWrapper =
                                        (TextInputLayout) content.findViewById(
                                                R.id.ed_comments_post_wrapper);
                                postCommentWrapper.setHint(
                                        content.getResources().getString(R.string.comment_after));
                                preComment.setText(commInfo.preComment);
                                postComment.setText(commInfo.postComment);
                                moveView.setText(commInfo.move);
                                String nagStr = Node.nagStr(commInfo.nag).trim();
                                if ((nagStr.length() == 0) && (commInfo.nag > 0)) {
                                    nagStr = String.format(Locale.US, "%d", commInfo.nag);
                                }
                                nag.setText(nagStr);

                                new MaterialDialog.Builder(MDChess.this)
                                        .title(R.string.edit_comments)
                                        .customView(content, true)
                                        .positiveText(android.R.string.ok)
                                        .negativeText(R.string.cancel)
                                        .onPositive(new MaterialDialog.SingleButtonCallback() {
                                            @Override
                                            public void onClick(@NonNull MaterialDialog dialog,
                                                    @NonNull DialogAction which) {
                                                String pre = preComment.getText().toString().trim();
                                                String post =
                                                        postComment.getText().toString().trim();
                                                int nagVal = Node.strToNag(
                                                        nag.getText().toString());

                                                MDChessController.CommentInfo commInfo =
                                                        new MDChessController.CommentInfo();
                                                commInfo.preComment = pre;
                                                commInfo.postComment = post;
                                                commInfo.nag = nagVal;
                                                ctrl.setComments(commInfo);
                                            }
                                        })
                                        .onNegative(new MaterialDialog.SingleButtonCallback() {
                                            @Override
                                            public void onClick(@NonNull MaterialDialog dialog,
                                                    @NonNull DialogAction which) {

                                            }
                                        })
                                        .show();

                                break;
                            }
                            case REMOVE_SUBTREE:
                                ctrl.removeSubTree();
                                break;
                            case MOVE_VAR_UP:
                                ctrl.moveVariation(-1);
                                break;
                            case MOVE_VAR_DOWN:
                                ctrl.moveVariation(1);
                                break;
                            case ADD_NULL_MOVE:
                                ctrl.makeHumanNullMove();
                                break;
                        }
                    }
                })
                .show();
    }

    private void thinkingMenuDialog() {
        final int ADD_ANALYSIS = 0;
        final int MULTIPV_DEC = 1;
        final int MULTIPV_INC = 2;
        final int HIDE_STATISTICS = 3;
        final int SHOW_STATISTICS = 4;
        List<CharSequence> lst = new ArrayList<>();
        List<Integer> actions = new ArrayList<>();
        lst.add(getString(R.string.add_analysis));
        actions.add(ADD_ANALYSIS);
        int numPV = this.numPV;
        if (gameMode.analysisMode()) {
            int maxPV = ctrl.maxPV();
            numPV = Math.min(numPV, maxPV);
            numPV = Math.max(numPV, 1);
            if (numPV > 1) {
                lst.add(getString(R.string.fewer_variations));
                actions.add(MULTIPV_DEC);
            }
            if (numPV < maxPV) {
                lst.add(getString(R.string.more_variations));
                actions.add(MULTIPV_INC);
            }
        }
        final int numPVF = numPV;
        if (thinkingStr1.length() > 0) {
            if (mShowStats) {
                lst.add(getString(R.string.hide_statistics));
                actions.add(HIDE_STATISTICS);
            } else {
                lst.add(getString(R.string.show_statistics));
                actions.add(SHOW_STATISTICS);
            }
        }
        final List<Integer> finalActions = actions;
        new MaterialDialog.Builder(this)
                .title(R.string.analysis)
                .items(lst)
                .itemsCallback(new MaterialDialog.ListCallback() {
                    @Override
                    public void onSelection(MaterialDialog dialog, View view, int which,
                            CharSequence text) {
                        switch (finalActions.get(which)) {
                            case ADD_ANALYSIS: {
                                ArrayList<ArrayList<Move>> pvMovesTmp = pvMoves;
                                String[] pvStrs = thinkingStr1.split("\n");
                                for (int i = 0; i < pvMovesTmp.size(); i++) {
                                    ArrayList<Move> pv = pvMovesTmp.get(i);
                                    StringBuilder preComment = new StringBuilder();
                                    if (i < pvStrs.length) {
                                        String[] tmp = pvStrs[i].split(" ");
                                        for (int j = 0; j < 2; j++) {
                                            if (j < tmp.length) {
                                                if (j > 0) preComment.append(' ');
                                                preComment.append(tmp[j]);
                                            }
                                        }
                                        if (preComment.length() > 0) preComment.append(':');
                                    }
                                    boolean updateDefault = (i == 0);
                                    ctrl.addVariation(preComment.toString(), pv, updateDefault);
                                }
                                break;
                            }
                            case MULTIPV_DEC:
                                setMultiPVMode(numPVF - 1);
                                break;
                            case MULTIPV_INC:
                                setMultiPVMode(numPVF + 1);
                                break;
                            case HIDE_STATISTICS:
                            case SHOW_STATISTICS: {
                                mShowStats = finalActions.get(which) == SHOW_STATISTICS;
                                Editor editor = settings.edit();
                                editor.putBoolean("showStats", mShowStats);
                                editor.apply();
                                updateThinkingInfo();
                                break;
                            }
                        }
                    }
                })
                .show();
    }

    private void setMultiPVMode(int nPV) {
        numPV = nPV;
        Editor editor = settings.edit();
        editor.putInt("numPV", numPV);
        editor.apply();
        ctrl.setMultiPVMode(numPV);
    }

    private void goBackMenuDialog() {
        final int GOTO_START_GAME = 0;
        final int GOTO_START_VAR = 1;
        final int GOTO_PREV_VAR = 2;
        final int LOAD_PREV_GAME = 3;
        final int AUTO_BACKWARD = 4;

        setAutoMode(AutoMode.OFF);
        List<CharSequence> lst = new ArrayList<>();
        List<Integer> actions = new ArrayList<>();
        lst.add(getString(R.string.goto_start_game));
        actions.add(GOTO_START_GAME);
        lst.add(getString(R.string.goto_start_variation));
        actions.add(GOTO_START_VAR);
        if (ctrl.currVariation() > 0) {
            lst.add(getString(R.string.goto_prev_variation));
            actions.add(GOTO_PREV_VAR);
        }
        final int currFT = currFileType();
        final String currPathName = currPathName();
        if ((currFT != FT_NONE) && !gameMode.clocksActive()) {
            lst.add(getString(R.string.load_prev_game));
            actions.add(LOAD_PREV_GAME);
        }
        if (!gameMode.clocksActive()) {
            lst.add(getString(R.string.auto_backward));
            actions.add(AUTO_BACKWARD);
        }
        final List<Integer> finalActions = actions;
        new MaterialDialog.Builder(this)
                .title(R.string.go_back)
                .items(lst)
                .itemsCallback(new MaterialDialog.ListCallback() {
                    @Override
                    public void onSelection(MaterialDialog dialog, View view, int which,
                            CharSequence text) {
                        switch (finalActions.get(which)) {
                            case GOTO_START_GAME:
                                ctrl.gotoMove(0);
                                break;
                            case GOTO_START_VAR:
                                ctrl.gotoStartOfVariation();
                                break;
                            case GOTO_PREV_VAR:
                                ctrl.changeVariation(-1);
                                break;
                            case LOAD_PREV_GAME:
                                Intent i;
                                if (currFT == FT_PGN) {
                                    i = new Intent(MDChess.this, EditPGNLoad.class);
                                    i.setAction("org.mdc.chess.loadFilePrevGame");
                                    i.putExtra("org.mdc.chess.pathname", currPathName);
                                    startActivityForResult(i, RESULT_LOAD_PGN);
                                } else if (currFT == FT_SCID) {
                                    i = new Intent(MDChess.this, LoadScid.class);
                                    i.setAction("org.mdc.chess.loadScidPrevGame");
                                    i.putExtra("org.mdc.chess.pathname", currPathName);
                                    startActivityForResult(i, RESULT_LOAD_PGN);
                                } else if (currFT == FT_FEN) {
                                    i = new Intent(MDChess.this, LoadFEN.class);
                                    i.setAction("org.mdc.chess.loadPrevFen");
                                    i.putExtra("org.mdc.chess.pathname", currPathName);
                                    startActivityForResult(i, RESULT_LOAD_FEN);
                                }
                                break;
                            case AUTO_BACKWARD:
                                setAutoMode(AutoMode.BACKWARD);
                                break;
                        }
                    }
                })
                .show();
    }

    private void goForwardMenuDialog() {
        final int GOTO_END_VAR = 0;
        final int GOTO_NEXT_VAR = 1;
        final int LOAD_NEXT_GAME = 2;
        final int AUTO_FORWARD = 3;

        setAutoMode(AutoMode.OFF);
        List<CharSequence> lst = new ArrayList<>();
        List<Integer> actions = new ArrayList<>();
        lst.add(getString(R.string.goto_end_variation));
        actions.add(GOTO_END_VAR);
        if (ctrl.currVariation() < ctrl.numVariations() - 1) {
            lst.add(getString(R.string.goto_next_variation));
            actions.add(GOTO_NEXT_VAR);
        }
        final int currFT = currFileType();
        final String currPathName = currPathName();
        if ((currFT != FT_NONE) && !gameMode.clocksActive()) {
            lst.add(getString(R.string.load_next_game));
            actions.add(LOAD_NEXT_GAME);
        }
        if (!gameMode.clocksActive()) {
            lst.add(getString(R.string.auto_forward));
            actions.add(AUTO_FORWARD);
        }
        final List<Integer> finalActions = actions;
        new MaterialDialog.Builder(this)
                .title(R.string.go_forward)
                .items(lst)
                .itemsCallback(new MaterialDialog.ListCallback() {
                    @Override
                    public void onSelection(MaterialDialog dialog, View view, int which,
                            CharSequence text) {
                        switch (finalActions.get(which)) {
                            case GOTO_END_VAR:
                                ctrl.gotoMove(Integer.MAX_VALUE);
                                break;
                            case GOTO_NEXT_VAR:
                                ctrl.changeVariation(1);
                                break;
                            case LOAD_NEXT_GAME:
                                Intent i;
                                if (currFT == FT_PGN) {
                                    i = new Intent(MDChess.this, EditPGNLoad.class);
                                    i.setAction("org.mdc.chess.loadFileNextGame");
                                    i.putExtra("org.mdc.chess.pathname", currPathName);
                                    startActivityForResult(i, RESULT_LOAD_PGN);
                                } else if (currFT == FT_SCID) {
                                    i = new Intent(MDChess.this, LoadScid.class);
                                    i.setAction("org.mdc.chess.loadScidNextGame");
                                    i.putExtra("org.mdc.chess.pathname", currPathName);
                                    startActivityForResult(i, RESULT_LOAD_PGN);
                                } else if (currFT == FT_FEN) {
                                    i = new Intent(MDChess.this, LoadFEN.class);
                                    i.setAction("org.mdc.chess.loadNextFen");
                                    i.putExtra("org.mdc.chess.pathname", currPathName);
                                    startActivityForResult(i, RESULT_LOAD_FEN);
                                }
                                break;
                            case AUTO_FORWARD:
                                setAutoMode(AutoMode.FORWARD);
                                break;
                        }
                    }
                })
                .show();

    }

    private void manageEnginesDialog() {
        final int SELECT_ENGINE = 0;
        final int SET_ENGINE_OPTIONS = 1;
        final int CONFIG_NET_ENGINE = 2;
        List<CharSequence> lst = new ArrayList<>();
        List<Integer> actions = new ArrayList<>();
        lst.add(getString(R.string.select_engine));
        actions.add(SELECT_ENGINE);
        if (canSetEngineOptions()) {
            lst.add(getString(R.string.set_engine_options));
            actions.add(SET_ENGINE_OPTIONS);
        }
        lst.add(getString(R.string.configure_network_engine));
        actions.add(CONFIG_NET_ENGINE);
        final List<Integer> finalActions = actions;
        new MaterialDialog.Builder(this)
                .title(R.string.option_manage_engines)
                .items(lst)
                .itemsCallback(new MaterialDialog.ListCallback() {
                    @Override
                    public void onSelection(MaterialDialog dialog, View view, int which,
                            CharSequence text) {
                        switch (finalActions.get(which)) {
                            case SELECT_ENGINE:

                                selectEngineDialog(false);
                                break;
                            case SET_ENGINE_OPTIONS:
                                setEngineOptions();
                                break;
                            case CONFIG_NET_ENGINE:
                                networkEngineDialog();
                                break;
                        }
                    }
                })
                .show();
    }

    /**
     * Return true if engine UCI options can be set now.
     */
    private boolean canSetEngineOptions() {
        if (!storageAvailable()) {
            return false;
        }
        UCIOptions uciOpts = ctrl.getUCIOptions();
        if (uciOpts == null) {
            return false;
        }
        for (String name : uciOpts.getOptionNames()) {
            if (uciOpts.getOption(name).visible) {
                return true;
            }
        }
        return false;
    }

    /**
     * Start activity to set engine options.
     */
    private void setEngineOptions() {
        Intent i = new Intent(MDChess.this, EditOptions.class);
        UCIOptions uciOpts = ctrl.getUCIOptions();
        if (uciOpts != null) {
            i.putExtra("org.mdc.chess.ucioptions", uciOpts);
            i.putExtra("org.mdc.chess.enginename", engineTitleText.getText());
            startActivityForResult(i, RESULT_EDITOPTIONS);
        }
    }

    private void networkEngineDialog() {
        String[] fileNames = findFilesInDirectory(engineDir, new FileNameFilter() {
            @Override
            public boolean accept(String filename) {
                return !reservedEngineName(filename) && EngineUtil.isNetEngine(filename);
            }
        });
        final int numFiles = fileNames.length;
        final int numItems = numFiles + 1;
        final String[] items = new String[numItems];
        final String[] ids = new String[numItems];
        int idx = 0;
        String sep = File.separator;
        String base = Environment.getExternalStorageDirectory() + sep + engineDir + sep;
        for (String fileName : fileNames) {
            ids[idx] = base + fileName;
            items[idx] = fileName;
            idx++;
        }
        ids[idx] = "";
        items[idx] = getString(R.string.new_engine);
        //idx++;
        String currEngine = ctrl.getEngine();
        int defaultItem = 0;
        for (int i = 0; i < numItems; i++) {
            if (ids[i].equals(currEngine)) {
                defaultItem = i;
                break;
            }
        }
        new MaterialDialog.Builder(this)
                .title(R.string.configure_network_engine)
                .items(items)
                .itemsCallbackSingleChoice(defaultItem,
                        new MaterialDialog.ListCallbackSingleChoice() {
                            @Override
                            public boolean onSelection(MaterialDialog dialog, View view, int which,
                                    CharSequence text) {
                                /**
                                 * If you use alwaysCallSingleChoiceCallback(), which is
                                 * discussed below,
                                 * returning false here won't allow the newly selected radio
                                 * button to actually be selected.
                                 **/
                                if (which == numItems - 1) {
                                    newNetworkEngineDialog();
                                } else {
                                    networkEngineToConfig = ids[which];
                                    networkEngineConfigDialog();
                                }
                                return true;
                            }
                        })
                .negativeText(R.string.cancel)
                .onNegative(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog,
                            @NonNull DialogAction which) {
                        manageEnginesDialog();
                    }
                })
                .show();
    }

    // Ask for name of new network engine
    private void newNetworkEngineDialog() {
        View content = View.inflate(this, R.layout.create_network_engine, null);
        final EditText engineNameView = (EditText) content.findViewById(R.id.create_network_engine);
        final TextInputLayout engineNameWrapper = (TextInputLayout) content.findViewById(
                R.id.create_network_engine_wrapper);
        engineNameWrapper.setHint(content.getResources().getString(R.string.engine_name));
        engineNameView.setText("");
        final Runnable createEngine = new Runnable() {
            public void run() {
                String engineName = engineNameView.getText().toString();
                String sep = File.separator;
                String pathName = Environment.getExternalStorageDirectory() + sep + engineDir + sep
                        + engineName;
                File file = new File(pathName);
                boolean nameOk = true;
                int errMsg = -1;
                if (engineName.contains("/")) {
                    nameOk = false;
                    errMsg = R.string.slash_not_allowed;
                } else if (reservedEngineName(engineName) || file.exists()) {
                    nameOk = false;
                    errMsg = R.string.engine_name_in_use;
                }
                if (!nameOk) {
                    Toast.makeText(getApplicationContext(), errMsg, Toast.LENGTH_LONG).show();
                    networkEngineDialog();
                    return;
                }
                networkEngineToConfig = pathName;
                networkEngineConfigDialog();
            }
        };
        new MaterialDialog.Builder(this)
                .title(R.string.create_network_engine)
                .customView(content, true)
                .positiveText(android.R.string.ok)
                .negativeText(R.string.cancel)
                .onPositive(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog,
                            @NonNull DialogAction which) {
                        createEngine.run();
                    }
                })
                .onNegative(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog,
                            @NonNull DialogAction which) {
                        networkEngineDialog();
                    }
                })
                .show();

        engineNameView.setOnKeyListener(new OnKeyListener() {
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if ((event.getAction() == KeyEvent.ACTION_DOWN) && (keyCode
                        == KeyEvent.KEYCODE_ENTER)) {
                    createEngine.run();
                    return true;
                }
                return false;
            }
        });
    }

    // Configure network engine settings
    private void networkEngineConfigDialog() {
        View content = View.inflate(this, R.layout.network_engine_config, null);
        final EditText hostNameView = (EditText) content.findViewById(R.id.network_engine_host);
        final EditText portView = (EditText) content.findViewById(R.id.network_engine_port);
        String hostName = "";
        String port = "0";
        try {
            if (EngineUtil.isNetEngine(networkEngineToConfig)) {
                String[] lines = Util.readFile(networkEngineToConfig);
                if (lines.length > 1) {
                    hostName = lines[1];
                }
                if (lines.length > 2) {
                    port = lines[2];
                }
            }
        } catch (IOException e1) {
            Log.d("Exception", e1.toString());
        }
        hostNameView.setText(hostName);
        portView.setText(port);
        final Runnable writeConfig = new Runnable() {
            public void run() {
                String hostName = hostNameView.getText().toString();
                String port = portView.getText().toString();
                try {
                    FileWriter fw = new FileWriter(new File(networkEngineToConfig), false);
                    fw.write("NETE\n");
                    fw.write(hostName);
                    fw.write("\n");
                    fw.write(port);
                    fw.write("\n");
                    fw.close();
                    setEngineOptions(true);
                } catch (IOException e) {
                    Toast.makeText(getApplicationContext(), e.getMessage(),
                            Toast.LENGTH_LONG).show();
                }
            }
        };
        new MaterialDialog.Builder(this)
                .title(R.string.configure_network_engine)
                .customView(content, true)
                .positiveText(android.R.string.ok)
                .negativeText(R.string.cancel)
                .neutralText(R.string.delete)
                .onPositive(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog,
                            @NonNull DialogAction which) {
                        writeConfig.run();
                        networkEngineDialog();
                    }
                })
                .onNeutral(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog,
                            @NonNull DialogAction which) {
                        deleteNetworkEngineDialog();
                    }
                })
                .onNegative(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog,
                            @NonNull DialogAction which) {
                        networkEngineDialog();
                    }
                });

        portView.setOnKeyListener(new OnKeyListener() {
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if ((event.getAction() == KeyEvent.ACTION_DOWN) && (keyCode
                        == KeyEvent.KEYCODE_ENTER)) {
                    writeConfig.run();
                    networkEngineDialog();
                    return true;
                }
                return false;
            }
        });
    }

    private void deleteNetworkEngineDialog() {
        String msg = networkEngineToConfig;
        if (msg.lastIndexOf('/') >= 0) {
            msg = msg.substring(msg.lastIndexOf('/') + 1);
        }
        new MaterialDialog.Builder(this)
                .title(R.string.delete_network_engine)
                .content(getString(R.string.network_engine) + ": " + msg)
                .positiveText(R.string.yes)
                .negativeText(R.string.no)
                .onPositive(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog,
                            @NonNull DialogAction which) {
                        boolean result = new File(networkEngineToConfig).delete();
                        Log.d("Result delete", "" + result);
                        String engine = settings.getString("engine", "stockfish");
                        if (engine.equals(networkEngineToConfig)) {
                            engine = "stockfish";
                            Editor editor = settings.edit();
                            editor.putString("engine", engine);
                            editor.apply();
                            dialog.dismiss();
                            int strength = settings.getInt("strength", 1000);
                            setEngineOptions(false);
                            setEngineStrength(engine, strength);
                        }
                        dialog.cancel();
                        networkEngineDialog();
                    }
                })
                .onNegative(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog,
                            @NonNull DialogAction which) {
                        networkEngineDialog();
                    }
                })
                .show();
    }

    /**
     * Open a load/save file dialog. Uses OI file manager if available.
     */
    private void selectFile(int titleMsg, int buttonMsg, String settingsName, String defaultDir,
            int dialog, int result) {
        setAutoMode(AutoMode.OFF);
        String action = "org.openintents.action.PICK_FILE";
        Intent i = new Intent(action);
        String currentFile = settings.getString(settingsName, "");
        String sep = File.separator;
        if (!currentFile.contains(sep)) {
            currentFile = Environment.getExternalStorageDirectory() +
                    sep + defaultDir + sep + currentFile;
        }
        i.setData(Uri.fromFile(new File(currentFile)));
        i.putExtra("org.openintents.extra.TITLE", getString(titleMsg));
        i.putExtra("org.openintents.extra.BUTTON_TEXT", getString(buttonMsg));
        try {
            startActivityForResult(i, result);
        } catch (ActivityNotFoundException e) {
            if (dialog == SELECT_PGN_FILE_SAVE_DIALOG) {
                selectPgnFileSaveDialog();
            } else if (dialog == SELECT_FEN_FILE_DIALOG) {
                selectFenFileDialog();
            }

        }
    }

    private boolean hasScidProvider() {
        try {
            getPackageManager().getPackageInfo("org.scid.android", 0);
            return true;
        } catch (PackageManager.NameNotFoundException ex) {
            return false;
        }
    }

    private void selectScidFile() {
        setAutoMode(AutoMode.OFF);
        Intent intent = new Intent();
        intent.setComponent(new ComponentName("org.scid.android",
                "org.scid.android.SelectFileActivity"));
        intent.setAction(".si4");
        try {
            startActivityForResult(intent, RESULT_SELECT_SCID);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void getFen() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("application/x-chess-fen");
        try {
            startActivityForResult(i, RESULT_GET_FEN);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private int currFileType() {
        return settings.getInt("currFT", FT_NONE);
    }

    /**
     * Return path name for the last used PGN or SCID file.
     */
    private String currPathName() {
        int ft = settings.getInt("currFT", FT_NONE);
        switch (ft) {
            case FT_PGN: {
                String ret = settings.getString("currentPGNFile", "");
                String sep = File.separator;
                if (!ret.contains(sep)) {
                    ret = Environment.getExternalStorageDirectory() + sep + pgnDir + sep + ret;
                }
                return ret;
            }
            case FT_SCID:
                return settings.getString("currentScidFile", "");
            case FT_FEN:
                return settings.getString("currentFENFile", "");
            default:
                return "";
        }
    }

    private String[] findFilesInDirectory(String dirName, final FileNameFilter filter) {
        File extDir = Environment.getExternalStorageDirectory();
        String sep = File.separator;
        File dir = new File(extDir.getAbsolutePath() + sep + dirName);
        File[] files = dir.listFiles(new FileFilter() {
            public boolean accept(File pathname) {
                return pathname.isFile() && ((filter == null) || filter.accept(
                        pathname.getAbsolutePath()));
            }
        });
        if (files == null) {
            files = new File[0];
        }
        final int numFiles = files.length;
        String[] fileNames = new String[numFiles];
        for (int i = 0; i < files.length; i++) {
            fileNames[i] = files[i].getName();
        }
        Arrays.sort(fileNames, String.CASE_INSENSITIVE_ORDER);
        return fileNames;
    }

    /**
     * Save current game to a PGN file.
     */
    private void savePGNToFile(String pathName) {
        String pgn = ctrl.getPGN();
        Editor editor = settings.edit();
        editor.putString("currentPGNFile", pathName);
        editor.putInt("currFT", FT_PGN);
        editor.apply();
        Intent i = new Intent(MDChess.this, EditPGNSave.class);
        i.setAction("org.mdc.chess.saveFile");
        i.putExtra("org.mdc.chess.pathname", pathName);
        i.putExtra("org.mdc.chess.pgn", pgn);
        i.putExtra("org.mdc.chess.silent", false);
        startActivity(i);
    }

    /**
     * Load a PGN game from a file.
     */
    private void loadPGNFromFile(String pathName) {
        Editor editor = settings.edit();
        editor.putString("currentPGNFile", pathName);
        editor.putInt("currFT", FT_PGN);
        editor.apply();
        Intent i = new Intent(MDChess.this, EditPGNLoad.class);
        i.setAction("org.mdc.chess.loadFile");
        i.putExtra("org.mdc.chess.pathname", pathName);
        startActivityForResult(i, RESULT_LOAD_PGN);
    }

    /**
     * Load a FEN position from a file.
     */
    private void loadFENFromFile(String pathName) {
        if (pathName == null) {
            return;
        }
        Editor editor = settings.edit();
        editor.putString("currentFENFile", pathName);
        editor.putInt("currFT", FT_FEN);
        editor.apply();
        Intent i = new Intent(MDChess.this, LoadFEN.class);
        i.setAction("org.mdc.chess.loadFen");
        i.putExtra("org.mdc.chess.pathname", pathName);
        startActivityForResult(i, RESULT_LOAD_FEN);
    }

    private void setFenHelper(String fen) {
        if (fen == null) {
            return;
        }
        try {
            ctrl.setFENOrPGN(fen);
        } catch (ChessParseError e) {
            // If FEN corresponds to illegal chess position, go into edit board mode.
            try {
                TextIO.readFEN(fen);
            } catch (ChessParseError e2) {
                if (e2.pos != null) {
                    startEditBoard(fen);
                }
            }
        }
    }

    @Override
    public void requestPromotePiece() {
        promoteDialog();
    }

    @Override
    public void reportInvalidMove(Move m) {
        String msg = String.format(Locale.US, "%s %s-%s",
                getString(R.string.invalid_move),
                TextIO.squareToString(m.from), TextIO.squareToString(m.to));
        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void reportEngineName(String engine) {
        String msg = String.format(Locale.US, "%s: %s",
                getString(R.string.engine), engine);
        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void reportEngineError(String errMsg) {
        String msg = String.format(Locale.US, "%s: %s",
                getString(R.string.engine_error), errMsg);
        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
    }

    @Override
    public void computerMoveMade() {
        if (soundEnabled) {
            if (moveSound != null) {
                moveSound.release();
            }
            try {
                moveSound = MediaPlayer.create(this, R.raw.movesound);
                if (moveSound != null) {
                    moveSound.start();
                }
            } catch (NotFoundException ex) {
                Log.d("Exception", ex.toString());
            }
        }
        if (vibrateEnabled) {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            v.vibrate(500);
        }
    }

    @Override
    public void runOnUIThread(Runnable runnable) {
        runOnUiThread(runnable);
    }

    /**
     * Decide if user should be warned about heavy CPU usage.
     */
    private void updateNotification() {
        boolean warn = false;
        if (lastVisibleMillis != 0) { // GUI not visible
            warn = lastComputationMillis >= lastVisibleMillis + 60000;
        }
        setNotification(warn);
    }

    /**
     * Set/clear the "heavy CPU usage" notification.
     */
    private void setNotification(boolean show) {
        if (notificationActive == show) {
            return;
        }
        notificationActive = show;
        final int cpuUsage = 1;
        String ns = Context.NOTIFICATION_SERVICE;
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(ns);
        if (show) {
            int icon = R.mipmap.ic_launcher;
            CharSequence tickerText = getString(R.string.heavy_cpu_usage);
            long when = System.currentTimeMillis();
            Context context = getApplicationContext();
            CharSequence contentTitle = getString(R.string.background_processing);
            CharSequence contentText = getString(R.string.lot_cpu_power);
            Intent notificationIntent = new Intent(this, CPUWarning.class);
            PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
            @SuppressWarnings("deprecation")
            Notification notification = new Notification.Builder(context)
                    .setSmallIcon(icon)
                    .setTicker(tickerText)
                    .setWhen(when)
                    .setOngoing(true)
                    .setContentTitle(contentTitle)
                    .setContentText(contentText)
                    .setContentIntent(contentIntent)
                    .getNotification();
            mNotificationManager.notify(cpuUsage, notification);
        } else {
            mNotificationManager.cancel(cpuUsage);
        }
    }

    private String timeToString(int time) {
        int secs = (int) Math.floor((time + 999) / 1000.0);
        boolean neg = false;
        if (secs < 0) {
            neg = true;
            secs = -secs;
        }
        int mins = secs / 60;
        secs -= mins * 60;
        StringBuilder ret = new StringBuilder();
        if (neg) ret.append('-');
        ret.append(mins);
        ret.append(':');
        if (secs < 10) ret.append('0');
        ret.append(secs);
        return ret.toString();
    }

    @Override
    public void setRemainingTime(int wTime, int bTime, int nextUpdate) {
        if (ctrl.getGameMode().clocksActive()) {
            whiteTitleText.setText(
                    getString(R.string.white_square_character) + " " + timeToString(wTime));
            blackTitleText.setText(
                    getString(R.string.black_square_character) + " " + timeToString(bTime));
        } else {
            TreeMap<String, String> headers = new TreeMap<>();
            ctrl.getHeaders(headers);
            whiteTitleText.setText(headers.get("White"));
            blackTitleText.setText(headers.get("Black"));
        }
        handlerTimer.removeCallbacks(r);
        if (nextUpdate > 0) {
            handlerTimer.postDelayed(r, nextUpdate);
        }
    }

    /**
     * Set automatic move forward/backward mode.
     */
    private void setAutoMode(AutoMode am) {

        autoMode = am;
        switch (am) {
            case BACKWARD:
            case FORWARD:
                if (autoMoveDelay > 0) {
                    autoModeTimer.postDelayed(amRunnable, autoMoveDelay);
                }
                break;
            case OFF:
                autoModeTimer.removeCallbacks(amRunnable);
                break;
        }
    }

    /**
     * Disable automatic move mode if clocks are active.
     */
    private void maybeAutoModeOff(GameMode gm) {
        if (gm.clocksActive()) {
            setAutoMode(AutoMode.OFF);
        }
    }

    private enum AutoMode {
        OFF, FORWARD, BACKWARD
    }

    /**
     * State of requested permissions.
     */
    private enum PermissionState {
        UNKNOWN,
        REQUESTED,
        GRANTED,
        DENIED
    }

    private interface Loader {
        void load(String pathName);
    }

    private interface FileNameFilter {
        boolean accept(String filename);
    }

    /**
     * PngTokenReceiver implementation that renders PGN data for screen display.
     */
    static class PgnScreenText implements PgnToken.PgnTokenReceiver,
            MoveListView.OnLinkClickListener {
        // --Commented out by Inspection (21/10/2016 11:38 PM):Node currNode = null;
        final static int indentStep = 15;
        final PGNOptions options;
        final HashMap<Node, NodeInfo> nodeToCharPos;
        private final TreeMap<Integer, Node> offs2Node = new TreeMap<>();
        int nestLevel = 0;
        boolean col0 = true;
        int currPos = 0, endPos = 0;
        boolean upToDate = false;
        int paraStart = 0;
        int paraIndent = 0;
        boolean paraBold = false;
        boolean pendingNewLine = false;
        BackgroundColorSpan bgSpan = new BackgroundColorSpan(Color.parseColor("#FFFFFF"));
        private SpannableStringBuilder sb = new SpannableStringBuilder();
        private int prevType = PgnToken.EOF;

        PgnScreenText(PGNOptions options) {
            nodeToCharPos = new HashMap<>();
            this.options = options;
        }

        public final CharSequence getText() {
            return sb;
        }

        public final int getCurrPos() {
            return currPos;
        }

        public boolean isUpToDate() {
            return upToDate;
        }

        private void newLine() {
            newLine(false);
        }

        private void newLine(boolean eof) {
            if (!col0) {
                if (paraIndent > 0) {
                    int paraEnd = sb.length();
                    int indent = paraIndent * indentStep;
                    sb.setSpan(new LeadingMarginSpan.Standard(indent), paraStart, paraEnd,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                if (paraBold) {
                    int paraEnd = sb.length();
                    sb.setSpan(new StyleSpan(Typeface.BOLD), paraStart, paraEnd,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                if (!eof) {
                    sb.append('\n');
                }
                paraStart = sb.length();
                paraIndent = nestLevel;
                paraBold = false;
            }
            col0 = true;
        }

        private void addMoveLink(Node node, int l0, int l1) {
            offs2Node.put(l0, node);
            offs2Node.put(l1, null);
        }

        @Override
        public boolean onLinkClick(int offs) {
            if (ctrl == null) {
                return false;
            }
            Map.Entry<Integer, Node> e = offs2Node.floorEntry(offs);
            if (e == null) {
                return false;
            }
            Node node = e.getValue();
            if (node == null && e.getKey() == offs) {
                e = offs2Node.lowerEntry(e.getKey());
                if (e != null) {
                    node = e.getValue();
                }
            }
            if (node == null) {
                return false;
            }

            // On android 4.1 this onClick method is called
            // even when you long click the move list. The test
            // below works around the problem.
            /*Dialog mlmd = moveListMenuDlg;
            if ((mlmd == null) || !mlmd.isShowing()) {
                df.setAutoMode(AutoMode.OFF);
                ctrl.goNode(node);
            }*/
            return true;
        }

        public void processToken(Node node, int type, String token) {
            if ((prevType == PgnToken.RIGHT_BRACKET) &&
                    (type != PgnToken.LEFT_BRACKET)) {
                if (options.view.headers) {
                    col0 = false;
                    newLine();
                } else {
                    sb.clear();
                    paraBold = false;
                }
            }
            if (pendingNewLine) {
                if (type != PgnToken.RIGHT_PAREN) {
                    newLine();
                    pendingNewLine = false;
                }
            }
            switch (type) {
                case PgnToken.STRING:
                    sb.append(" \"");
                    sb.append(token);
                    sb.append('"');
                    break;
                case PgnToken.INTEGER:
                    if ((prevType != PgnToken.LEFT_PAREN) &&
                            (prevType != PgnToken.RIGHT_BRACKET) && !col0) {
                        sb.append(' ');
                    }
                    sb.append(token);
                    col0 = false;
                    break;
                case PgnToken.PERIOD:
                    sb.append('.');
                    col0 = false;
                    break;
                case PgnToken.ASTERISK:
                    sb.append(" *");
                    col0 = false;
                    break;
                case PgnToken.LEFT_BRACKET:
                    sb.append('[');
                    col0 = false;
                    break;
                case PgnToken.RIGHT_BRACKET:
                    sb.append("]\n");
                    col0 = false;
                    break;
                case PgnToken.LEFT_PAREN:
                    nestLevel++;
                    if (col0) {
                        paraIndent++;
                    }
                    newLine();
                    sb.append('(');
                    col0 = false;
                    break;
                case PgnToken.RIGHT_PAREN:
                    sb.append(')');
                    nestLevel--;
                    pendingNewLine = true;
                    break;
                case PgnToken.NAG:
                    sb.append(Node.nagStr(Integer.parseInt(token)));
                    col0 = false;
                    break;
                case PgnToken.SYMBOL: {
                    if ((prevType != PgnToken.RIGHT_BRACKET) && (prevType != PgnToken.LEFT_BRACKET)
                            && !col0) {
                        sb.append(' ');
                    }
                    int l0 = sb.length();
                    sb.append(token);
                    int l1 = sb.length();
                    nodeToCharPos.put(node, new NodeInfo(l0, l1));
                    addMoveLink(node, l0, l1);
                    if (endPos < l0) endPos = l0;
                    col0 = false;
                    if (nestLevel == 0) paraBold = true;
                    break;
                }
                case PgnToken.COMMENT:
                    if (prevType == PgnToken.RIGHT_BRACKET) {
                        break;
                    } else if (nestLevel == 0) {
                        nestLevel++;
                        newLine();
                        nestLevel--;
                    } else {
                        if ((prevType != PgnToken.LEFT_PAREN) && !col0) {
                            sb.append(' ');
                        }
                    }
                    int l0 = sb.length();
                    sb.append(token.replaceAll("[ \t\r\n]+", " ").trim());
                    int l1 = sb.length();
                    int color = ColorTheme.instance().getColor(ColorTheme.PGN_COMMENT);
                    sb.setSpan(new ForegroundColorSpan(color), l0, l1,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    col0 = false;
                    if (nestLevel == 0) {
                        newLine();
                    }
                    break;
                case PgnToken.EOF:
                    newLine(true);
                    upToDate = true;
                    break;
            }
            prevType = type;
        }

        @Override
        public void clear() {
            sb = new SpannableStringBuilder();
            offs2Node.clear();
            prevType = PgnToken.EOF;
            nestLevel = 0;
            col0 = true;
            //currNode = null;
            currPos = 0;
            endPos = 0;
            nodeToCharPos.clear();
            paraStart = 0;
            paraIndent = 0;
            paraBold = false;
            pendingNewLine = false;

            upToDate = false;
        }

        @Override
        public void setCurrent(Node node) {
            sb.removeSpan(bgSpan);
            NodeInfo ni = nodeToCharPos.get(node);
            if ((ni == null) && (node != null) && (node.getParent() != null)) {
                ni = nodeToCharPos.get(node.getParent());
            }
            if (ni != null) {
                //int color = ColorTheme.instance().getColor(ColorTheme.CURRENT_MOVE);
                bgSpan = new BackgroundColorSpan(Color.WHITE);
                sb.setSpan(bgSpan, ni.l0, ni.l1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                currPos = ni.l0;
            } else {
                currPos = 0;
            }
            //currNode = node;
        }

        private static class NodeInfo {
            final int l0;
            final int l1;

            NodeInfo(int ls, int le) {
                l0 = ls;
                l1 = le;
            }
        }
    }
}
