/*
 * Open Chaos Chess is a free as in speech version of Chaos Chess
 * Chaos Chess is a chess game where you control the piece that moves, but not how it moves
 *     Copyright (C) 2019  Noah Stanford <noahstandingford@gmail.com>
 *
 *     Open Chaos Chess is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Open Chaos Chess is distributed in the hope that it will be fun,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package dev.corruptedark.openchaoschess;

import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

public class MultiPlayerBoard extends AppCompatActivity {
    public final int YOU = -1;
    public final int OPPONENT = 1;
    public final int NONE = 0;

    public final String NEW_GAME = "New Game?";
    public final String YES = "Yes";
    public final String NO = "No";
    public final String TIE = "Tie";
    public final String LOSS = "Loss";
    public final String WIN = "Win";
    public final String QUIT = "Quit";

    public final String TAG = "Multiplayer Board";

    private boolean isHost;

    int boardSize, squareSize, xPosition, yPosition;
    Square[][] board;
    Square defaultSquare;
    Square selected;
    Mover mover;
    MultiGame multiGame;
    ViewGroup boardMain;
    MultiPlayerBoard context;
    MoveThread moveThread;
    RelativeLayout boardLayout;
    NewGameAlertFragment newGameAlertFragment;

    private Square animatedSquare;

    private Thread newGameRequestThread;
    private Thread newGameListenerThread;
    private Thread moveOpponentThread;

    Toolbar toolbar;

    AchievementHandler achievementHandler;

    ColorManager colorManager;

    MultiPlayerService multiPlayerService;

    int boardColor1;
    int boardColor2;
    int selectColor;
    int pieceColor;

    TextView wonLabel, lostLabel, tieLabel,cantMoveThatLabel, notYourTurnLabel, gameOverLabel, thatSucksLabel, noiceLabel, yourPointLabel, opponentPointLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_multi_player_board);

        achievementHandler = AchievementHandler.getInstance(this);

        toolbar = (Toolbar) findViewById(R.id.multiplay_toolbar);
        setSupportActionBar(toolbar);

        if(getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }


        boardMain = (ViewGroup) findViewById(R.id.multiplay_board_layout);
        boardLayout = (RelativeLayout)findViewById(R.id.multiplay_board_layout);
        wonLabel = (TextView) findViewById(R.id.multiplay_won_label);
        lostLabel = (TextView) findViewById(R.id.multiplay_lost_label);
        cantMoveThatLabel = (TextView) findViewById(R.id.multiplay_cant_move_that_label);
        notYourTurnLabel = (TextView) findViewById(R.id.multiplay_not_your_turn_label);
        gameOverLabel = (TextView) findViewById(R.id.multiplay_game_over_label);
        thatSucksLabel = (TextView) findViewById(R.id.multiplay_that_sucks_label);
        noiceLabel = (TextView) findViewById(R.id.multiplay_noice_label);
        yourPointLabel = (TextView) findViewById(R.id.your_points);
        opponentPointLabel = (TextView) findViewById(R.id.opponent_points);
        tieLabel = (TextView) findViewById(R.id.tie_label);

        colorManager = ColorManager.getInstance(this);

        boardColor1 = colorManager.getColorFromFile(ColorManager.BOARD_COLOR_1);
        boardColor2 = colorManager.getColorFromFile(ColorManager.BOARD_COLOR_2);
        selectColor = colorManager.getColorFromFile(ColorManager.SELECTION_COLOR);
        pieceColor = colorManager.getColorFromFile(ColorManager.PIECE_COLOR);

        context = this;
        defaultSquare = new Square(this,pieceColor);
        selected = defaultSquare;
        selected.setPiece(Piece.NONE);
        boardSize = 8;

        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        int width = size.x;

        squareSize = (width-convertDpToPx(30))/8;
        xPosition = convertDpToPx(15);
        yPosition = convertDpToPx(160);

        mover = new Mover(this);
        multiGame = MultiGame.getInstance();
        multiGame.resetGames();

        board = new Square[boardSize][boardSize];
        for (int i = 0; i < boardSize; i++)
            for (int j = 0; j < boardSize; j++)
                board[i][ j] = new Square(this,pieceColor);
        startNewGame(getIntent().getBooleanExtra("knightsOnly",false));
        boardMain.setVisibility(View.VISIBLE);
        multiGame.newGame(isHost);
        isHost = getIntent().getBooleanExtra("isHost",false);
        if(isHost)
        {
            multiGame.setTurn(YOU);
        }
        else
        {
            multiGame.setTurn(OPPONENT);
        }

        animatedSquare = new Square(this,pieceColor);
        animatedSquare.setPiece(Piece.NONE);
        animatedSquare.setVisibility(View.GONE);
        animatedSquare.setX(0);
        animatedSquare.setY(0);
        animatedSquare.setBackgroundColor(Color.TRANSPARENT);
        animatedSquare.setLayoutParams(new RelativeLayout.LayoutParams(squareSize, squareSize));

        multiGame.setAnimatedSquare(animatedSquare);

        boardMain.addView(animatedSquare);

        yourPointLabel.setText(getResources().getText(R.string.your_points).toString()+ " " + multiGame.getYourPoints());
        opponentPointLabel.setText(getResources().getText(R.string.opponent_points).toString() + " " + multiGame.getOpponentPoints());

        wonLabel.bringToFront();
        lostLabel.bringToFront();
        tieLabel.bringToFront();
        cantMoveThatLabel.bringToFront();
        notYourTurnLabel.bringToFront();
        gameOverLabel.bringToFront();
        thatSucksLabel.bringToFront();
        animatedSquare.bringToFront();
        boardLayout.invalidate();

    }

    /*@Override
    protected void onResume() {
        super.onResume();

        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        int width = size.x;
        int height = size.y;

        int iconWidth = (int)(width * 0.40);
        int buttonHeight = (int)(height * .075);
        int buttonGap = (int)(height * .03);

        squareSize = (width-convertDpToPx(30))/8;
        xPosition = convertDpToPx(15);
        yPosition = convertDpToPx(160);

        mover = new Mover(this);
        multiGame = MultiGame.getInstance();
        multiGame.resetGames();

        board = new Square[boardSize][boardSize];
        for (int i = 0; i < boardSize; i++)
            for (int j = 0; j < boardSize; j++)
                board[i][ j] = new Square(this,pieceColor);
        startNewGame(getIntent().getBooleanExtra("knightsOnly",false));
        boardMain.setVisibility(View.VISIBLE);
        multiGame.newGame(isHost);
        isHost = getIntent().getBooleanExtra("isHost",false);
        if(isHost)
        {
            multiGame.setTurn(YOU);
        }
        else
        {
            multiGame.setTurn(OPPONENT);
        }

        yourPointLabel.setText(getResources().getText(R.string.your_points).toString()+ " " + multiGame.getYourPoints());
        opponentPointLabel.setText(getResources().getText(R.string.opponent_points).toString() + " " + multiGame.getOpponentPoints());

        wonLabel.bringToFront();
        lostLabel.bringToFront();
        tieLabel.bringToFront();
        cantMoveThatLabel.bringToFront();
        notYourTurnLabel.bringToFront();
        gameOverLabel.bringToFront();
        thatSucksLabel.bringToFront();
        boardLayout.invalidate();

        RelativeLayout.LayoutParams yourPointParams = new RelativeLayout.LayoutParams(2*iconWidth, (int)(height*.03));
        yourPointParams.addRule(RelativeLayout.BELOW, R.id.multiplay_toolbar);
        yourPointParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        yourPointParams.setMargins(buttonGap, buttonGap,0,0);
        yourPointLabel.setLayoutParams(yourPointParams);
        yourPointLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX,(int)(height*.025));
        yourPointLabel.setGravity(Gravity.LEFT);

        RelativeLayout.LayoutParams opponentPointParams = new RelativeLayout.LayoutParams(2*iconWidth, (int)(height*.03));
        opponentPointParams.addRule(RelativeLayout.BELOW, R.id.multiplay_toolbar);
        opponentPointParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        opponentPointParams.setMargins(0,buttonGap,buttonGap,0);
        opponentPointLabel.setLayoutParams(opponentPointParams);
        opponentPointLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX,(int)(height*.025));
        opponentPointLabel.setGravity(Gravity.RIGHT);

        RelativeLayout.LayoutParams wonParams = new RelativeLayout.LayoutParams(3*iconWidth, (int)(height*.03));
        wonParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
        wonParams.addRule(RelativeLayout.CENTER_VERTICAL);
        wonParams.setMargins(0, 0,0,0);
        wonLabel.setLayoutParams(wonParams);
        wonLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX,(int)(height*.025));
        wonLabel.setGravity(Gravity.CENTER);

        RelativeLayout.LayoutParams lostParams = new RelativeLayout.LayoutParams(3*iconWidth, (int)(height*.03));
        lostParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
        lostParams.addRule(RelativeLayout.CENTER_VERTICAL);
        lostParams.setMargins(0, 0,0,0);
        lostLabel.setLayoutParams(lostParams);
        lostLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX,(int)(height*.025));
        lostLabel.setGravity(Gravity.CENTER);

        RelativeLayout.LayoutParams tieParams = new RelativeLayout.LayoutParams(3*iconWidth, (int)(height*.03));
        tieParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
        tieParams.addRule(RelativeLayout.CENTER_VERTICAL);
        tieParams.setMargins(0, 0,0,0);
        tieLabel.setLayoutParams(tieParams);
        tieLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX,(int)(height*.025));
        tieLabel.setGravity(Gravity.CENTER);

        RelativeLayout.LayoutParams cantMoveParams = new RelativeLayout.LayoutParams(3*iconWidth, (int)(height*.03));
        cantMoveParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
        cantMoveParams.addRule(RelativeLayout.ABOVE, R.id.multiplay_not_your_turn_label);
        cantMoveParams.setMargins(0, 0,0,buttonGap);
        cantMoveThatLabel.setLayoutParams(cantMoveParams);
        cantMoveThatLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX,(int)(height*.025));
        cantMoveThatLabel.setGravity(Gravity.CENTER);

        RelativeLayout.LayoutParams noiceParams = new RelativeLayout.LayoutParams(iconWidth, (int)(height*.03));
        noiceParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
        noiceParams.addRule(RelativeLayout.BELOW, R.id.multiplay_toolbar);
        noiceParams.setMargins(0, 2*buttonGap,0,0);
        noiceLabel.setLayoutParams(noiceParams);
        noiceLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX,(int)(height*.025));
        noiceLabel.setGravity(Gravity.CENTER);

        RelativeLayout.LayoutParams notYourTurnParams = new RelativeLayout.LayoutParams(3*iconWidth, (int)(height*.03));
        notYourTurnParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
        notYourTurnParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        notYourTurnParams.setMargins(0, 0,0,buttonGap);
        notYourTurnLabel.setLayoutParams(notYourTurnParams);
        notYourTurnLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX,(int)(height*.025));
        notYourTurnLabel.setGravity(Gravity.CENTER);

        RelativeLayout.LayoutParams gameOverParams = new RelativeLayout.LayoutParams(2*iconWidth, (int)(height*.03));
        gameOverParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
        gameOverParams.addRule(RelativeLayout.ABOVE,R.id.multiplay_lost_label);
        gameOverParams.setMargins(0, 0,0,buttonGap);
        gameOverLabel.setLayoutParams(gameOverParams);
        gameOverLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX,(int)(height*.025));
        gameOverLabel.setGravity(Gravity.CENTER);

        RelativeLayout.LayoutParams thatSucksParams = new RelativeLayout.LayoutParams(2*iconWidth, (int)(height*.03));
        thatSucksParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
        thatSucksParams.addRule(RelativeLayout.BELOW, R.id.multiplay_toolbar);
        thatSucksParams.setMargins(0, 2*buttonGap,0,0);
        thatSucksLabel.setLayoutParams(thatSucksParams);
        thatSucksLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX,(int)(height*.02));
        thatSucksLabel.setGravity(Gravity.CENTER);

        boardLayout.setBackgroundColor(colorManager.getColorFromFile(ColorManager.BACKGROUND_COLOR));
        toolbar.setBackgroundColor(colorManager.getColorFromFile(ColorManager.SECONDARY_COLOR));
        toolbar.setTitleTextColor(colorManager.getColorFromFile(ColorManager.TEXT_COLOR));
        wonLabel.setTextColor(colorManager.getColorFromFile(ColorManager.TEXT_COLOR));
        lostLabel.setTextColor(colorManager.getColorFromFile(ColorManager.TEXT_COLOR));
        tieLabel.setTextColor(colorManager.getColorFromFile(ColorManager.TEXT_COLOR));
        yourPointLabel.setTextColor(colorManager.getColorFromFile(ColorManager.TEXT_COLOR));
        opponentPointLabel.setTextColor(colorManager.getColorFromFile(ColorManager.TEXT_COLOR));
        cantMoveThatLabel.setTextColor(colorManager.getColorFromFile(ColorManager.TEXT_COLOR));
        noiceLabel.setTextColor(colorManager.getColorFromFile(ColorManager.TEXT_COLOR));
        notYourTurnLabel.setTextColor(colorManager.getColorFromFile(ColorManager.TEXT_COLOR));
        gameOverLabel.setTextColor(colorManager.getColorFromFile(ColorManager.TEXT_COLOR));
        thatSucksLabel.setTextColor(colorManager.getColorFromFile(ColorManager.TEXT_COLOR));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(colorManager.getColorFromFile(ColorManager.BAR_COLOR));
        }

        if(multiGame.getTurn() == OPPONENT)
        {
            moveOpponent();
        }

    }*/

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);

        int width = size.x;
        int height = size.y;

        int iconWidth = (int)(width * 0.40);
        int buttonHeight = (int)(height * .075);
        int buttonGap = (int)(height * .03);

        RelativeLayout.LayoutParams yourPointParams = new RelativeLayout.LayoutParams(2*iconWidth, (int)(height*.03));
        yourPointParams.addRule(RelativeLayout.BELOW, R.id.multiplay_toolbar);
        yourPointParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        yourPointParams.setMargins(buttonGap, buttonGap,0,0);
        yourPointLabel.setLayoutParams(yourPointParams);
        yourPointLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX,(int)(height*.025));
        yourPointLabel.setGravity(Gravity.LEFT);

        RelativeLayout.LayoutParams opponentPointParams = new RelativeLayout.LayoutParams(2*iconWidth, (int)(height*.03));
        opponentPointParams.addRule(RelativeLayout.BELOW, R.id.multiplay_toolbar);
        opponentPointParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        opponentPointParams.setMargins(0,buttonGap,buttonGap,0);
        opponentPointLabel.setLayoutParams(opponentPointParams);
        opponentPointLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX,(int)(height*.025));
        opponentPointLabel.setGravity(Gravity.RIGHT);

        RelativeLayout.LayoutParams wonParams = new RelativeLayout.LayoutParams(3*iconWidth, (int)(height*.03));
        wonParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
        wonParams.addRule(RelativeLayout.CENTER_VERTICAL);
        wonParams.setMargins(0, 0,0,0);
        wonLabel.setLayoutParams(wonParams);
        wonLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX,(int)(height*.025));
        wonLabel.setGravity(Gravity.CENTER);

        RelativeLayout.LayoutParams lostParams = new RelativeLayout.LayoutParams(3*iconWidth, (int)(height*.03));
        lostParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
        lostParams.addRule(RelativeLayout.CENTER_VERTICAL);
        lostParams.setMargins(0, 0,0,0);
        lostLabel.setLayoutParams(lostParams);
        lostLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX,(int)(height*.025));
        lostLabel.setGravity(Gravity.CENTER);

        RelativeLayout.LayoutParams tieParams = new RelativeLayout.LayoutParams(3*iconWidth, (int)(height*.03));
        tieParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
        tieParams.addRule(RelativeLayout.CENTER_VERTICAL);
        tieParams.setMargins(0, 0,0,0);
        tieLabel.setLayoutParams(tieParams);
        tieLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX,(int)(height*.025));
        tieLabel.setGravity(Gravity.CENTER);

        RelativeLayout.LayoutParams cantMoveParams = new RelativeLayout.LayoutParams(3*iconWidth, (int)(height*.03));
        cantMoveParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
        cantMoveParams.addRule(RelativeLayout.ABOVE, R.id.multiplay_not_your_turn_label);
        cantMoveParams.setMargins(0, 0,0,buttonGap);
        cantMoveThatLabel.setLayoutParams(cantMoveParams);
        cantMoveThatLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX,(int)(height*.025));
        cantMoveThatLabel.setGravity(Gravity.CENTER);

        RelativeLayout.LayoutParams noiceParams = new RelativeLayout.LayoutParams(iconWidth, (int)(height*.03));
        noiceParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
        noiceParams.addRule(RelativeLayout.BELOW, R.id.multiplay_toolbar);
        noiceParams.setMargins(0, 2*buttonGap,0,0);
        noiceLabel.setLayoutParams(noiceParams);
        noiceLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX,(int)(height*.025));
        noiceLabel.setGravity(Gravity.CENTER);

        RelativeLayout.LayoutParams notYourTurnParams = new RelativeLayout.LayoutParams(3*iconWidth, (int)(height*.03));
        notYourTurnParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
        notYourTurnParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        notYourTurnParams.setMargins(0, 0,0,buttonGap);
        notYourTurnLabel.setLayoutParams(notYourTurnParams);
        notYourTurnLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX,(int)(height*.025));
        notYourTurnLabel.setGravity(Gravity.CENTER);

        RelativeLayout.LayoutParams gameOverParams = new RelativeLayout.LayoutParams(2*iconWidth, (int)(height*.03));
        gameOverParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
        gameOverParams.addRule(RelativeLayout.ABOVE,R.id.multiplay_lost_label);
        gameOverParams.setMargins(0, 0,0,buttonGap);
        gameOverLabel.setLayoutParams(gameOverParams);
        gameOverLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX,(int)(height*.025));
        gameOverLabel.setGravity(Gravity.CENTER);

        RelativeLayout.LayoutParams thatSucksParams = new RelativeLayout.LayoutParams(2*iconWidth, (int)(height*.03));
        thatSucksParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
        thatSucksParams.addRule(RelativeLayout.BELOW, R.id.multiplay_toolbar);
        thatSucksParams.setMargins(0, 2*buttonGap,0,0);
        thatSucksLabel.setLayoutParams(thatSucksParams);
        thatSucksLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX,(int)(height*.02));
        thatSucksLabel.setGravity(Gravity.CENTER);

        boardLayout.setBackgroundColor(colorManager.getColorFromFile(ColorManager.BACKGROUND_COLOR));
        toolbar.setBackgroundColor(colorManager.getColorFromFile(ColorManager.SECONDARY_COLOR));
        toolbar.setTitleTextColor(colorManager.getColorFromFile(ColorManager.TEXT_COLOR));
        wonLabel.setTextColor(colorManager.getColorFromFile(ColorManager.TEXT_COLOR));
        lostLabel.setTextColor(colorManager.getColorFromFile(ColorManager.TEXT_COLOR));
        tieLabel.setTextColor(colorManager.getColorFromFile(ColorManager.TEXT_COLOR));
        yourPointLabel.setTextColor(colorManager.getColorFromFile(ColorManager.TEXT_COLOR));
        opponentPointLabel.setTextColor(colorManager.getColorFromFile(ColorManager.TEXT_COLOR));
        cantMoveThatLabel.setTextColor(colorManager.getColorFromFile(ColorManager.TEXT_COLOR));
        noiceLabel.setTextColor(colorManager.getColorFromFile(ColorManager.TEXT_COLOR));
        notYourTurnLabel.setTextColor(colorManager.getColorFromFile(ColorManager.TEXT_COLOR));
        gameOverLabel.setTextColor(colorManager.getColorFromFile(ColorManager.TEXT_COLOR));
        thatSucksLabel.setTextColor(colorManager.getColorFromFile(ColorManager.TEXT_COLOR));

        newGameAlertFragment = new NewGameAlertFragment(MultiPlayerBoard.this,TAG,isHost);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(colorManager.getColorFromFile(ColorManager.BAR_COLOR));
        }

        if(multiGame.getTurn() == OPPONENT)
        {
            moveOpponent();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        MenuItem newGameButton = menu.findItem(R.id.new_game);

        newGameButton.setVisible(isHost);
        newGameButton.setEnabled(isHost);

        return true;
    }

    @Override
    public void onBackPressed() {

        for(int i = 0; i < boardSize; i++)
            for(int j = 0; j < boardSize; j++)
                boardMain.removeView(board[i][j]);

        if(multiPlayerService != null) {
            multiPlayerService.cancel();
        }

        if(moveOpponentThread != null)
        {
            moveOpponentThread.interrupt();
        }

        if(newGameRequestThread != null)
        {
            newGameRequestThread.interrupt();
        }

        if(newGameListenerThread != null)
        {
            newGameListenerThread.interrupt();
        }

        Toast.makeText(this,"Connection ended", Toast.LENGTH_LONG).show();

        this.finish();
        super.onBackPressed();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.new_game:
                newGameButton_Click();
                return true;
            case android.R.id.home:
                //multiGame.saveBoard(board);
                onBackPressed();
                return true;
            default:
                return super.onOptionsItemSelected(item);

        }
    }

    private int convertDpToPx(int dp){
        return Math.round(dp*(getResources().getDisplayMetrics().xdpi/ DisplayMetrics.DENSITY_DEFAULT));
    }

    private void newGameButton_Click()
    {
        if(multiGame.getTurn() == NONE && (newGameRequestThread == null || !newGameRequestThread.isAlive())) {
            newGameRequestThread = new Thread() {
                @Override
                public void run() {
                    super.run();
                    Looper.prepare();

                    multiPlayerService = GameConnectionHandler.getMultiPlayerService(context);
                    multiPlayerService.sendData(context,NEW_GAME);


                    Toast.makeText(MultiPlayerBoard.this, "New game requested", Toast.LENGTH_LONG).show();


                    while (!multiPlayerService.hasNewMessage(context) && !isInterrupted()) {
                        Log.v(TAG,"Waiting for new game response");
                        try {
                            Thread.sleep(500);
                        }
                        catch (InterruptedException interruptException)
                        {
                            return;
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    if(isInterrupted())
                    {
                        return;
                    }

                    if (multiPlayerService.getMostRecentData(context).equals(YES)) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                tieLabel.setVisibility(View.INVISIBLE);
                                wonLabel.setVisibility(View.INVISIBLE);
                                lostLabel.setVisibility(View.INVISIBLE);
                                cantMoveThatLabel.setVisibility(View.INVISIBLE);
                                notYourTurnLabel.setVisibility(View.INVISIBLE);
                                gameOverLabel.setVisibility(View.INVISIBLE);
                                thatSucksLabel.setVisibility(View.INVISIBLE);
                                noiceLabel.setVisibility(View.INVISIBLE);

                                while (moveThread != null && moveThread.isAlive() && !isInterrupted());

                                if(isInterrupted())
                                {
                                    return;
                                }

                                selected = defaultSquare;
                                clearPieces();
                                multiGame.newGame(isHost);
                                if (isHost) {
                                    multiGame.setTurn(YOU);
                                } else {
                                    multiGame.setTurn(OPPONENT);
                                }
                                startNewGame(getIntent().getBooleanExtra("knightsOnly", false));
                                yourPointLabel.setText(getResources().getText(R.string.your_points).toString() + " " + multiGame.getYourPoints());
                                opponentPointLabel.setText(getResources().getText(R.string.opponent_points).toString() + " " + multiGame.getOpponentPoints());
                            }
                        });
                    } else {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                onBackPressed();
                            }
                        });
                    }
                }
            };

            newGameRequestThread.start();
        }
    }

    private void listenForNewGame()
    {
        if(!isHost && multiGame.getTurn() == NONE && (newGameListenerThread == null || !newGameRequestThread.isAlive())) {
            newGameListenerThread = new Thread() {
                @Override
                public void run() {
                    super.run();


                    while (!multiPlayerService.hasNewMessage(context)) {
                        Log.v(TAG, "Listening for new game");
                        try {
                            Thread.sleep(500);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }


                    String received = multiPlayerService.getMostRecentData(context);
                    if (received.equals(NEW_GAME))
                    {

                        Looper.prepare();

                        Log.v(TAG, "Trying to show alert");

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                newGameAlertFragment.show(getSupportFragmentManager(),"newGameAlert");
                            }
                        });
                    }
                }
            };

            newGameListenerThread.start();
        }
    }

    void clearPieces()
    {
        for (int i = 0; i < boardSize; i++)
            for (int j = 0; j < boardSize; j++)
            {
                board[i][ j].setTeam(NONE);
                board[i][ j].setPiece(Piece.NONE);
                board[i][ j].setPieceCount(0);
            }

        return;
    }

    void startNewGame(boolean knightsOnly)
    {
        drawBoard(knightsOnly, boardSize);
        return;
    }

    public boolean bishopTie(){
        boolean bishopTie;
        List<Square> yous = multiGame.movableYous(mover,board);
        List<Square> opponents = multiGame.movableOpponents(mover,board);

        boolean allYousAreBishops = true;
        boolean allOpponentsAreBishops = true;

        for(int i = 0; i < yous.size(); i++)
            if(yous.get(i).getPiece() != Piece.BISHOP){
                allYousAreBishops = false;
                break;
            }
        for(int i = 0; i < opponents.size(); i++)
            if(opponents.get(i).getPiece() != Piece.BISHOP){
                allOpponentsAreBishops = false;
                break;
            }

        if(allOpponentsAreBishops && allYousAreBishops && (yous.size() == 1) && (opponents.size() == 1) && (yous.get(0).getColor() != opponents.get(0).getColor()))
            bishopTie = true;
        else
            bishopTie = false;

        return bishopTie;
    }

    public synchronized void moveSelectedButton_Click(final View view)
    {
        achievementHandler.incrementInMemory(AchievementHandler.STARTED_GAME);
        achievementHandler.saveValues();
        thatSucksLabel.post(new Runnable() {
            @Override
            public void run() {
                thatSucksLabel.setVisibility(View.INVISIBLE);
            }
        });
        noiceLabel.post(new Runnable() {
            @Override
            public void run() {
                noiceLabel.setVisibility(View.INVISIBLE);
            }
        });
        int yourScore = multiGame.getYourPoints();
        if (multiGame.getTurn() == YOU)
        {
            if(!multiGame.getCanOpponentMove(mover,board) && !multiGame.getCanYouMove(mover,board)){
                cantMoveThatLabel.post(new Runnable() {
                    @Override
                    public void run() {
                        cantMoveThatLabel.setVisibility(View.VISIBLE);
                    }
                });
                gameOverLabel.post(new Runnable() {
                    @Override
                    public void run() {
                        gameOverLabel.setVisibility(View.VISIBLE);
                    }
                });
                multiGame.setTurn(NONE);
                if(multiGame.getOpponentPoints() == multiGame.getYourPoints())
                    tieLabel.post(new Runnable() {
                        @Override
                        public void run() {
                            tieLabel.setVisibility(View.VISIBLE);
                            achievementHandler.incrementInMemory(AchievementHandler.TIED_A_GAME);
                            achievementHandler.incrementInMemory(AchievementHandler.TIED_10_GAMES);
                            achievementHandler.incrementInMemory(AchievementHandler.TIED_50_GAMES);
                            achievementHandler.incrementInMemory(AchievementHandler.PLAYED_10_GAMES);
                            achievementHandler.incrementInMemory(AchievementHandler.PLAYED_50_GAMES);
                            achievementHandler.incrementInMemory(AchievementHandler.PLAYED_100_GAMES);
                            achievementHandler.saveValues();
                            multiPlayerService = GameConnectionHandler.getMultiPlayerService(context);
                            multiPlayerService.sendData(context,TIE);
                            multiGame.setTurn(NONE);
                            listenForNewGame();
                        }
                    });
                else if(multiGame.getOpponentPoints() < multiGame.getYourPoints()) {
                    wonLabel.post(new Runnable() {
                        @Override
                        public void run() {
                            wonLabel.setVisibility(View.VISIBLE);
                            achievementHandler.incrementInMemory(AchievementHandler.WON_A_GAME);
                            achievementHandler.incrementInMemory(AchievementHandler.WON_10_GAMES);
                            achievementHandler.incrementInMemory(AchievementHandler.WON_50_GAMES);
                            achievementHandler.incrementInMemory(AchievementHandler.PLAYED_10_GAMES);
                            achievementHandler.incrementInMemory(AchievementHandler.PLAYED_50_GAMES);
                            achievementHandler.incrementInMemory(AchievementHandler.PLAYED_100_GAMES);

                            if (multiGame.getOpponentPoints() == 0) {
                                achievementHandler.incrementInMemory(AchievementHandler.UNTOUCHABLE);
                            }
                            achievementHandler.saveValues();
                            multiGame.setTurn(NONE);
                            listenForNewGame();
                        }
                    });

                }
                else
                    lostLabel.post(new Runnable() {
                        @Override
                        public void run() {
                            lostLabel.setVisibility(View.VISIBLE);
                            achievementHandler.incrementInMemory(AchievementHandler.LOST_A_GAME);
                            achievementHandler.incrementInMemory(AchievementHandler.LOST_10_GAMES);
                            achievementHandler.incrementInMemory(AchievementHandler.LOST_50_GAMES);
                            achievementHandler.incrementInMemory(AchievementHandler.PLAYED_10_GAMES);
                            achievementHandler.incrementInMemory(AchievementHandler.PLAYED_50_GAMES);
                            achievementHandler.incrementInMemory(AchievementHandler.PLAYED_100_GAMES);

                            if (multiGame.getYourPoints() == 0) {
                                achievementHandler.incrementInMemory(AchievementHandler.SLAUGHTERED);
                            }
                            achievementHandler.saveValues();
                            multiGame.setTurn(NONE);
                            listenForNewGame();
                        }
                    });
            }
            else if(bishopTie()){
                gameOverLabel.post(new Runnable() {
                    @Override
                    public void run() {
                        gameOverLabel.setVisibility(View.VISIBLE);
                    }
                });
                multiGame.setTurn(NONE);
                if(multiGame.getOpponentPoints()== multiGame.getYourPoints())
                    tieLabel.post(new Runnable() {
                        @Override
                        public void run() {
                            tieLabel.setVisibility(View.VISIBLE);
                            achievementHandler.incrementInMemory(AchievementHandler.TIED_A_GAME);
                            achievementHandler.incrementInMemory(AchievementHandler.TIED_10_GAMES);
                            achievementHandler.incrementInMemory(AchievementHandler.TIED_50_GAMES);
                            achievementHandler.incrementInMemory(AchievementHandler.PLAYED_10_GAMES);
                            achievementHandler.incrementInMemory(AchievementHandler.PLAYED_50_GAMES);
                            achievementHandler.incrementInMemory(AchievementHandler.PLAYED_100_GAMES);
                            achievementHandler.saveValues();
                            multiPlayerService = GameConnectionHandler.getMultiPlayerService(context);
                            multiPlayerService.sendData(context,TIE);
                            multiGame.setTurn(NONE);
                            listenForNewGame();
                        }
                    });
                else if(multiGame.getOpponentPoints()< multiGame.getYourPoints()) {
                    wonLabel.post(new Runnable() {
                        @Override
                        public void run() {
                            wonLabel.setVisibility(View.VISIBLE);
                            achievementHandler.incrementInMemory(AchievementHandler.WON_A_GAME);
                            achievementHandler.incrementInMemory(AchievementHandler.WON_10_GAMES);
                            achievementHandler.incrementInMemory(AchievementHandler.WON_50_GAMES);
                            achievementHandler.incrementInMemory(AchievementHandler.PLAYED_10_GAMES);
                            achievementHandler.incrementInMemory(AchievementHandler.PLAYED_50_GAMES);
                            achievementHandler.incrementInMemory(AchievementHandler.PLAYED_100_GAMES);

                            if (multiGame.getOpponentPoints() == 0) {
                                achievementHandler.incrementInMemory(AchievementHandler.UNTOUCHABLE);
                            }
                            achievementHandler.saveValues();
                            multiGame.setTurn(NONE);
                            listenForNewGame();
                        }
                    });

                }
                else
                    lostLabel.post(new Runnable() {
                        @Override
                        public void run() {
                            lostLabel.setVisibility(View.VISIBLE);
                            achievementHandler.incrementInMemory(AchievementHandler.LOST_A_GAME);
                            achievementHandler.incrementInMemory(AchievementHandler.LOST_10_GAMES);
                            achievementHandler.incrementInMemory(AchievementHandler.LOST_50_GAMES);
                            achievementHandler.incrementInMemory(AchievementHandler.PLAYED_10_GAMES);
                            achievementHandler.incrementInMemory(AchievementHandler.PLAYED_50_GAMES);
                            achievementHandler.incrementInMemory(AchievementHandler.PLAYED_100_GAMES);

                            if (multiGame.getYourPoints() == 0) {
                                achievementHandler.incrementInMemory(AchievementHandler.SLAUGHTERED);
                            }
                            achievementHandler.saveValues();
                            multiGame.setTurn(NONE);
                            listenForNewGame();
                        }
                    });
            } else if(selected.getI() == -1) {
                // Nothing selected
                cantMoveThatLabel.post(new Runnable() {
                    @Override
                    public void run() {
                        cantMoveThatLabel.setVisibility(View.INVISIBLE);
                    }
                });
                notYourTurnLabel.post(new Runnable() {
                    @Override
                    public void run() {
                        notYourTurnLabel.setVisibility(View.INVISIBLE);
                    }
                });


            } else if (mover.movePiece(board, board[selected.getI()][ selected.getJ()], multiGame)) {
                sendYourMove(selected, mover.getLastDestination());

                selected.post(new Runnable() {
                    @Override
                    public void run() {
                        int color;

                        if(selected.getColor())
                            color = boardColor1;
                        else
                            color = boardColor2;

                        selected.setBackgroundColor(color);
                        selected = defaultSquare;
                    }
                });

                if(multiGame.getYourPoints() > yourScore){
                    noiceLabel.post(new Runnable() {
                        @Override
                        public void run() {
                            noiceLabel.setVisibility(View.VISIBLE);
                            thatSucksLabel.setVisibility(View.INVISIBLE);
                        }
                    });
                }
                else {
                    noiceLabel.post(new Runnable() {
                        @Override
                        public void run() {
                            noiceLabel.setVisibility(View.INVISIBLE);
                        }
                    });
                }

                cantMoveThatLabel.post(new Runnable() {
                    @Override
                    public void run() {
                        cantMoveThatLabel.setVisibility(View.INVISIBLE);
                    }
                });
                notYourTurnLabel.post(new Runnable() {
                    @Override
                    public void run() {
                        notYourTurnLabel.setVisibility(View.INVISIBLE);
                    }
                });
                multiGame.incrementMoveCount();
                multiGame.setTurn(OPPONENT);


                yourPointLabel.post(new Runnable() {
                    @Override
                    public void run() {
                        yourPointLabel.setText(getResources().getText(R.string.your_points).toString() + " " + multiGame.getYourPoints());
                    }
                });

                do {
                    if (multiGame.getCanOpponentMove(mover, board))
                    {
                        moveOpponent();
                    }
                }  while(!multiGame.getCanYouMove(mover,board) && multiGame.getCanOpponentMove(mover,board) && multiGame.getYourCount() > 0);

                if (multiGame.getYourCount() == 0)
                {
                    lostLabel.post(new Runnable() {
                        @Override
                        public void run() {
                            lostLabel.setVisibility(View.VISIBLE);
                            achievementHandler.incrementInMemory(AchievementHandler.LOST_A_GAME);
                            achievementHandler.incrementInMemory(AchievementHandler.LOST_10_GAMES);
                            achievementHandler.incrementInMemory(AchievementHandler.LOST_50_GAMES);
                            achievementHandler.incrementInMemory(AchievementHandler.PLAYED_10_GAMES);
                            achievementHandler.incrementInMemory(AchievementHandler.PLAYED_50_GAMES);
                            achievementHandler.incrementInMemory(AchievementHandler.PLAYED_100_GAMES);

                            if (multiGame.getYourPoints() == 0) {
                                achievementHandler.incrementInMemory(AchievementHandler.SLAUGHTERED);
                            }
                            achievementHandler.saveValues();
                            multiGame.setTurn(NONE);
                            listenForNewGame();
                        }
                    });


                }
                else if (multiGame.getOpponentCount() == 0)
                {
                    wonLabel.post(new Runnable() {
                        @Override
                        public void run() {
                            wonLabel.setVisibility(View.VISIBLE);
                            achievementHandler.incrementInMemory(AchievementHandler.WON_A_GAME);
                            achievementHandler.incrementInMemory(AchievementHandler.WON_10_GAMES);
                            achievementHandler.incrementInMemory(AchievementHandler.WON_50_GAMES);
                            achievementHandler.incrementInMemory(AchievementHandler.PLAYED_10_GAMES);
                            achievementHandler.incrementInMemory(AchievementHandler.PLAYED_50_GAMES);
                            achievementHandler.incrementInMemory(AchievementHandler.PLAYED_100_GAMES);

                            if (multiGame.getOpponentPoints() == 0) {
                                achievementHandler.incrementInMemory(AchievementHandler.UNTOUCHABLE);
                            }
                            achievementHandler.saveValues();
                            multiGame.setTurn(NONE);
                            listenForNewGame();
                        }
                    });



                }
            }
            else {
                cantMoveThatLabel.post(new Runnable() {
                    @Override
                    public void run() {
                        cantMoveThatLabel.setVisibility(View.VISIBLE);
                    }
                });
                notYourTurnLabel.post(new Runnable() {
                    @Override
                    public void run() {
                        notYourTurnLabel.setVisibility(View.INVISIBLE);
                    }
                });

            }


        }
        else if (multiGame.getTurn() == OPPONENT) {
            if(multiGame.getCanOpponentMove(mover,board)) {
                cantMoveThatLabel.post(new Runnable() {
                    @Override
                    public void run() {
                        cantMoveThatLabel.setVisibility(View.VISIBLE);
                    }
                });
                notYourTurnLabel.post(new Runnable() {
                    @Override
                    public void run() {
                        notYourTurnLabel.setVisibility(View.VISIBLE);
                    }
                });
            }else{
                multiGame.setTurn(YOU);
                moveSelectedButton_Click(view);
            }
        }
        else {
            gameOverLabel.post(new Runnable() {
                @Override
                public void run() {
                    gameOverLabel.setVisibility(View.VISIBLE);
                }
            });

        }


    }


    synchronized void moveOpponent() {

        multiPlayerService = GameConnectionHandler.getMultiPlayerService(context);

        if(moveOpponentThread == null || !moveOpponentThread.isAlive())
            moveOpponentThread = new Thread(){
                @Override
                public void run() {
                    super.run();

                    thatSucksLabel.post(new Runnable() {
                        @Override
                        public void run() {
                            thatSucksLabel.setVisibility(View.INVISIBLE);
                        }
                    });
                    noiceLabel.post(new Runnable() {
                        @Override
                        public void run() {
                            noiceLabel.setVisibility(View.INVISIBLE);
                        }
                    });

                    while (!multiPlayerService.hasNewMessage(context) && !isInterrupted()) {
                        Log.v(TAG, "Waiting for move");

                        try {
                            Thread.sleep(500);
                        }
                        catch (InterruptedException interruptException)
                        {
                            return;
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    if (isInterrupted()) {
                        return;
                    }

                    String received = multiPlayerService.getMostRecentData(context);

                    if (received.equals(WIN)) {
                        lostLabel.post(new Runnable() {
                            @Override
                            public void run() {
                                lostLabel.setVisibility(View.VISIBLE);
                                achievementHandler.incrementInMemory(AchievementHandler.LOST_A_GAME);
                                achievementHandler.incrementInMemory(AchievementHandler.LOST_10_GAMES);
                                achievementHandler.incrementInMemory(AchievementHandler.LOST_50_GAMES);
                                achievementHandler.incrementInMemory(AchievementHandler.PLAYED_10_GAMES);
                                achievementHandler.incrementInMemory(AchievementHandler.PLAYED_50_GAMES);
                                achievementHandler.incrementInMemory(AchievementHandler.PLAYED_100_GAMES);

                                if (multiGame.getYourPoints() == 0) {
                                    achievementHandler.incrementInMemory(AchievementHandler.SLAUGHTERED);
                                }
                                achievementHandler.saveValues();
                            }
                        });

                        multiGame.setTurn(NONE);
                        listenForNewGame();
                    } else if (received.equals(TIE)) {
                        tieLabel.post(new Runnable() {
                            @Override
                            public void run() {
                                tieLabel.setVisibility(View.VISIBLE);
                                achievementHandler.incrementInMemory(AchievementHandler.TIED_A_GAME);
                                achievementHandler.incrementInMemory(AchievementHandler.TIED_10_GAMES);
                                achievementHandler.incrementInMemory(AchievementHandler.TIED_50_GAMES);
                                achievementHandler.incrementInMemory(AchievementHandler.PLAYED_10_GAMES);
                                achievementHandler.incrementInMemory(AchievementHandler.PLAYED_50_GAMES);
                                achievementHandler.incrementInMemory(AchievementHandler.PLAYED_100_GAMES);
                                achievementHandler.saveValues();
                            }
                        });
                        multiGame.setTurn(NONE);
                        listenForNewGame();
                    } else if (received.equals(LOSS)) {
                        lostLabel.post(new Runnable() {
                            @Override
                            public void run() {
                                lostLabel.setVisibility(View.VISIBLE);
                                achievementHandler.incrementInMemory(AchievementHandler.LOST_A_GAME);
                                achievementHandler.incrementInMemory(AchievementHandler.LOST_10_GAMES);
                                achievementHandler.incrementInMemory(AchievementHandler.LOST_50_GAMES);
                                achievementHandler.incrementInMemory(AchievementHandler.PLAYED_10_GAMES);
                                achievementHandler.incrementInMemory(AchievementHandler.PLAYED_50_GAMES);
                                achievementHandler.incrementInMemory(AchievementHandler.PLAYED_100_GAMES);

                                if (multiGame.getYourPoints() == 0) {
                                    achievementHandler.incrementInMemory(AchievementHandler.SLAUGHTERED);
                                }
                                achievementHandler.saveValues();
                            }
                        });

                        multiGame.setTurn(NONE);
                        listenForNewGame();
                    } else if (received.equals(NEW_GAME)) {

                        Looper.prepare();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                newGameAlertFragment.show(getSupportFragmentManager(),"newGameAlert");
                            }
                        });

                    } else {

                        String[] receivedPieces = received.split(",");

                        final int startI = Integer.parseInt(receivedPieces[0]);
                        final int startJ = boardSize - 1 - Integer.parseInt(receivedPieces[1]);
                        final int endI = Integer.parseInt(receivedPieces[2]);
                        final int endJ = boardSize - 1 - Integer.parseInt(receivedPieces[3]);

                        final Square startSquare = board[startI][startJ];
                        final Square endSquare = board[endI][endJ];
                        final int endSquareTeam = endSquare.getTeam();

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                endSquare.setTeam(OPPONENT);
                                if (startSquare.getPiece() == Piece.PAWN && endJ == 7) {
                                    endSquare.setPiece(Piece.QUEEN);
                                } else {
                                    endSquare.setPiece(startSquare.getPiece());
                                }
                                endSquare.setPieceCount(startSquare.getPieceCount() + 1);
                                startSquare.setPieceCount(0);
                                startSquare.setTeam(NONE);
                                startSquare.setPiece(Piece.NONE);


                                if (endSquareTeam == YOU) {
                                    multiGame.incrementOpponentPoints();
                                    thatSucksLabel.setVisibility(View.VISIBLE);
                                    noiceLabel.setVisibility(View.INVISIBLE);
                                } else {
                                    thatSucksLabel.setVisibility(View.INVISIBLE);
                                }

                                opponentPointLabel.setText(getResources().getText(R.string.opponent_points).toString() + " " + multiGame.getOpponentPoints());

                                boardLayout.invalidate();

                                if (multiGame.getOpponentPoints() >= 16) {
                                    lostLabel.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            lostLabel.setVisibility(View.VISIBLE);
                                            achievementHandler.incrementInMemory(AchievementHandler.LOST_A_GAME);
                                            achievementHandler.incrementInMemory(AchievementHandler.LOST_10_GAMES);
                                            achievementHandler.incrementInMemory(AchievementHandler.LOST_50_GAMES);
                                            achievementHandler.incrementInMemory(AchievementHandler.PLAYED_10_GAMES);
                                            achievementHandler.incrementInMemory(AchievementHandler.PLAYED_50_GAMES);
                                            achievementHandler.incrementInMemory(AchievementHandler.PLAYED_100_GAMES);

                                            if (multiGame.getYourPoints() == 0) {
                                                achievementHandler.incrementInMemory(AchievementHandler.SLAUGHTERED);
                                            }
                                            achievementHandler.saveValues();
                                        }
                                    });

                                    multiGame.setTurn(NONE);
                                    listenForNewGame();
                                } else if (multiGame.getCanYouMove(mover, board)) {
                                    multiGame.setTurn(YOU);
                                } else {
                                    multiGame.setTurn(OPPONENT);
                                    moveOpponent();
                                }
                            }
                        });
                    }
                }
            };

        moveOpponentThread.start();
    }

    synchronized void sendYourMove(Square selected, Square destination) {

        multiPlayerService = GameConnectionHandler.getMultiPlayerService(context);

        String data = selected.getI() + "," + selected.getJ() + "," + destination.getI() + "," + destination.getJ();

        multiPlayerService.sendData(context,data);

        Log.v(TAG, "Sent move");
    }

    void drawBoard(boolean knightsOnly, int size)
    {
        createSquares(size);

        startPieces(knightsOnly,size);

        return;
    }

    void createSquares(int size)
    {
        boolean colorPicker = false;
        int color;

        for (int i = 0; i < size; i++)
        {

            colorPicker = !colorPicker;
            for (int j = 0; j < size; j++)
            {

                if (colorPicker)
                    color = boardColor1;
                else
                    color = boardColor2;

                board[i][ j].setI(i);
                board[i][ j].setJ(j);
                board[i][ j].setColor(colorPicker);
                board[i][ j].setBackgroundColor(color);


                colorPicker = !colorPicker;
                board[i][ j].setX(xPosition + i * squareSize);
                board[i][ j].setY(yPosition + j * squareSize);
                board[i][ j].setLayoutParams(new RelativeLayout.LayoutParams(squareSize, squareSize));
                if (multiGame.getGameCount() == 0)
                {
                    board[i][ j].setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            if(moveThread == null || !moveThread.isAlive()) {
                                moveThread = new MoveThread(view, context, boardColor1, boardColor2, selectColor);
                                moveThread.start();
                            }
                        }
                    });
                    boardMain.addView(board[i][j]);
                    board[i][j].setVisibility(View.VISIBLE);
                }


            }
        }

        return;
    }

    void startPieces(boolean knightsOnly, int size)
    {
        if(knightsOnly)
        {
            // Set Teams and pieces
            for (int i = 0; i < size; i++) {
                board[i][0].setTeam(OPPONENT);
                board[i][0].setPiece(Piece.KNIGHT);
                board[i][1].setTeam(OPPONENT);
                board[i][1].setPiece(Piece.KNIGHT);
                board[i][6].setTeam(YOU);
                board[i][6].setPiece(Piece.KNIGHT);
                board[i][7].setTeam(YOU);
                board[i][7].setPiece(Piece.KNIGHT);
            }

        }
        else {

            // Set Teams
            for (int i = 0; i < size; i++) {
                board[i][0].setTeam(OPPONENT);
                board[i][1].setTeam(OPPONENT);
                board[i][6].setTeam(YOU);
                board[i][7].setTeam(YOU);
            }

            /*
            board[5][0].setPiece(Piece.BISHOP);
            board[5][7].setPiece(Piece.BISHOP);
             */

            // Set Pawns
            for (int i = 0; i < size; i++)
                board[i][6].setPiece(Piece.PAWN);

            for (int i = 0; i < size; i++)
                board[i][1].setPiece(Piece.PAWN);

            // Set Rooks
            board[0][0].setPiece(Piece.ROOK);
            board[7][0].setPiece(Piece.ROOK);
            board[0][7].setPiece(Piece.ROOK);
            board[7][7].setPiece(Piece.ROOK);

            // Set Knights
            board[1][0].setPiece(Piece.KNIGHT);
            board[6][0].setPiece(Piece.KNIGHT);
            board[1][7].setPiece(Piece.KNIGHT);
            board[6][7].setPiece(Piece.KNIGHT);

            // Set Bishops
            board[2][0].setPiece(Piece.BISHOP);
            board[5][0].setPiece(Piece.BISHOP);
            board[2][7].setPiece(Piece.BISHOP);
            board[5][7].setPiece(Piece.BISHOP);

            // Set Kings
            board[3][0].setPiece(Piece.KING);
            board[3][7].setPiece(Piece.KING);

            // Set Queens
            board[4][0].setPiece(Piece.QUEEN);
            board[4][7].setPiece(Piece.QUEEN);

        }

        return;
    }


}
