import tester.Tester;
import java.util.ArrayList;
import java.util.Arrays;
import javalib.worldimages.*;
import javalib.impworld.*;
import java.awt.Color;
import java.util.Collections;

// Represents a card in the Concentration game
class Card {
  String suit; // The suit of the card
  String value; // The value of the card
  boolean isFaceUp; // Indicates whether the card is face-up

  Card(String s, String v) {
    this.suit = s;
    this.value = v;
    this.isFaceUp = false; // Default to face-down
  }

  // Gets the card value
  public String getValue() {
    return this.value;
  }

  // Gets the card suit
  public String getSuit() {
    return this.suit;
  }

  // Checks if the card is face-up
  public boolean isFaceUp() {
    return this.isFaceUp;
  }

  // Flips the card
  public void flip() {
    this.isFaceUp = !this.isFaceUp;
  }

  // Converts a cards value and suit to a text representation
  public String toString() {
    if (isFaceUp) {
      return this.value + this.suit;
    }
    else {
      return "Face Down";
    }
  }

  // Checks if this card matches another card based on value and color
  public boolean isMatch(Card other) {
    boolean valuesMatch = this.value.equals(other.getValue());
    boolean suitsMatch = (this.isRedSuit() && other.isRedSuit())
        || (this.isBlackSuit() && other.isBlackSuit());
    return valuesMatch && suitsMatch;
  }

  // Check if the suit is red
  public boolean isRedSuit() {
    return this.suit.equals("♥") || this.suit.equals("♦");
  }

  // Check if the suit is black
  public boolean isBlackSuit() {
    return this.suit.equals("♠") || this.suit.equals("♣");
  }
}

//Represents the Concentration game world
class ConcentrationWorld extends World {
  ArrayList<ArrayList<Card>> board; // The grid of cards
  int pairsLeft; // Number of pairs left to match
  Card firstSelected; // The first selected card
  Card secondSelected; // The second selected card
  boolean waitingForMatch; // Indicates if the game is waiting to check a match
  int elapsedTime; // Time elapsed since the game started
  int score; // Player's score
  int totalGuesses = 0; // Number of guesses made
  int maxSteps; // Maximum steps/guesses allowed before game over
  boolean gameWon; // Whether the game is won
  boolean gameOver; // Whether the game is over

  // Default constructor: creates a shuffled board
  ConcentrationWorld() {
    this(createBoard());
  }

  // Takes a custom board for testing
  ConcentrationWorld(ArrayList<ArrayList<Card>> board) {
    this.board = board;
    this.pairsLeft = 1; // Always 26 pairs in a 52 card deck
    this.firstSelected = null;
    this.secondSelected = null;
    this.waitingForMatch = false;
    this.elapsedTime = 0;
    this.score = 26; // Since 26 pairs there are 26 pairs to find
    this.totalGuesses = 0;
    this.maxSteps = 100; // Allow up to 100 guesses
    this.gameWon = false;
    this.gameOver = false;
  }

  // Creates and shuffles a deck of cards, and deals them into a 4x13 grid
  public static ArrayList<ArrayList<Card>> createBoard() {
    ArrayList<String> values = new ArrayList<>(
        Arrays.asList("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13"));
    ArrayList<String> suits = new ArrayList<>(Arrays.asList("♥", "♦", "♠", "♣"));
    ArrayList<Card> deck = new ArrayList<>();

    // Generate the deck
    for (String suit : suits) {
      for (String value : values) {
        deck.add(new Card(suit, value));
      }
    }
    Collections.shuffle(deck);

    // Split the shuffled deck into a 4x13 grid
    ArrayList<ArrayList<Card>> board = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      ArrayList<Card> row = new ArrayList<>();
      for (int j = 0; j < 13; j++) {
        row.add(deck.get(i * 13 + j));
      }
      board.add(row);
    }
    return board;
  }

  // Renders the game
  public WorldScene makeScene() {
    WorldScene scene = new WorldScene(750, 500);

    // Draws green background (i.e. a play mat)
    scene.placeImageXY(new RectangleImage(750, 437, OutlineMode.SOLID, new Color(1, 82, 15)), 375,
        125);
    // Draws the game name
    scene.placeImageXY(new TextImage("Concentration", 25, Color.BLACK), 350, 321);

    // Draw each card on the board
    for (int row = 0; row < 4; row++) {
      for (int col = 0; col < 13; col++) {
        Card card = this.board.get(row).get(col);
        int x = 50 + (col * 50);
        int y = 50 + (row * 75);

        // Creates an outline for all cards
        RectangleImage cardOutline = new RectangleImage(38, 50, OutlineMode.OUTLINE, Color.WHITE);
        scene.placeImageXY(cardOutline, x, y);

        if (card.isFaceUp()) {
          // Draw the card's value and suit if face-up. Default text color is black so we
          // must have an if statement for red cards to change the color of the text.
          Color textColor = Color.BLACK;
          if (card.getSuit().equals("♥") || card.getSuit().equals("♦")) {
            textColor = Color.RED;
          }
          scene.placeImageXY(new TextImage(card.toString(), 20, textColor), x, y);
        }
        else {
          // Draw the back of the card if face-down
          scene.placeImageXY(new RectangleImage(37, 49, OutlineMode.SOLID, Color.RED), x + 1,
              y + 1);
          scene.placeImageXY(new CircleImage(3, OutlineMode.SOLID, Color.WHITE), x, y);
          scene.placeImageXY(new CircleImage(3, OutlineMode.SOLID, Color.WHITE), x + 10, y + 15);
          scene.placeImageXY(new CircleImage(3, OutlineMode.SOLID, Color.WHITE), x + 10, y - 15);
          scene.placeImageXY(new CircleImage(3, OutlineMode.SOLID, Color.WHITE), x - 10, y + 15);
          scene.placeImageXY(new CircleImage(3, OutlineMode.SOLID, Color.WHITE), x - 10, y - 15);
        }
      }
    }

    int secondsElapsed = (int) (this.elapsedTime / 30);
    int guessesMade = this.totalGuesses;

    // Displays the time elapsed
    scene.placeImageXY(new TextImage("Time: " + secondsElapsed + "s", 25, Color.RED), 638, 475);
    // Displays the score
    scene.placeImageXY(new TextImage("Score: " + score, 25, Color.BLUE), 475, 475);
    // Displays the amount of guesses the player has made
    scene.placeImageXY(new TextImage("Guesses: " + totalGuesses, 25, Color.BLACK), 300, 475);
    // Displays the amount of steps/guesses the player has left before a game over
    scene.placeImageXY(new TextImage("Steps Left: " + maxSteps, 25, Color.MAGENTA), 100, 475);
    // Displays text if the game is won showing the amount of guesses and time it
    // took to win
    if (gameWon) {
      scene.placeImageXY(new TextImage(
          "You win! Final Score: " + guessesMade + " guesses in " + secondsElapsed + " seconds!",
          25, Color.GREEN), 350, 375);
    }
    // Displays text if the game is lost/over
    else if (gameOver) {
      scene.placeImageXY(new TextImage("Game Over! Steps exhausted!", 25, Color.RED), 350, 375);
    }
    return scene;
  }

  // Handles mouse clicks to flip cards and process selections
  public void onMouseClicked(Posn pos) {
    if (waitingForMatch || gameOver || gameWon) {
      return; // Ignore clicks if game is paused or ended
    }

    // Dimensions and spacing for cards used for calculations
    int cardWidth = 38;
    int cardHeight = 50;
    int xOffset = 50;
    int yOffset = 50;
    int colSpacing = 50;
    int rowSpacing = 75;

    // Calculate the row and column of the clicked position
    int col = (pos.x - xOffset + (colSpacing / 2)) / colSpacing;
    int row = (pos.y - yOffset + (rowSpacing / 2)) / rowSpacing;

    // Check if the click is within bounds
    if (row >= 0 && row < 4 && col >= 0 && col < 13) {
      Card clickedCard = this.board.get(row).get(col);

      // Verify the click is within the card's dimensions
      int cardX = xOffset + col * colSpacing;
      int cardY = yOffset + row * rowSpacing;

      if (Math.abs(pos.x - cardX) <= cardWidth / 2 && Math.abs(pos.y - cardY) <= cardHeight / 2) {
        // Flip the card if it's face-down
        if (!clickedCard.isFaceUp()) {
          clickedCard.flip();
          if (firstSelected == null) {
            firstSelected = clickedCard; // Set as the first card
          }
          else if (secondSelected == null) {
            secondSelected = clickedCard; // Set as the second card
            waitingForMatch = true; // Wait to process match
          }
        }
      }
    }
  }

  // Update the game on Tick
  int flipTimer = 0; // Timer to control card flip duration

  public void onTick() {
    // Stops the game if game is won or lost
    if (this.gameWon || this.gameOver) {
      return;
    }

    // Increases the time on every tick
    this.elapsedTime++;

    // Handles checking of the first and second selected cards, updating game state,
    // and setting values such as score, guesses, ect.
    if (waitingForMatch) {
      flipTimer++;
      if (flipTimer >= 30) {
        if (firstSelected.isMatch(secondSelected)) {
          pairsLeft--;
          score = Math.max(0, score - 1);
          totalGuesses++;
          maxSteps = Math.max(0, maxSteps - 1);

          firstSelected = null;
          secondSelected = null;
          waitingForMatch = false;

          if (pairsLeft == 0) {
            this.gameWon = true;
          }
          if (maxSteps == 0) {
            this.gameOver = true;
          }
        }
        else {
          totalGuesses++;
          maxSteps = Math.max(0, maxSteps - 1);

          firstSelected.flip();
          secondSelected.flip();

          firstSelected = null;
          secondSelected = null;
          waitingForMatch = false;

          if (maxSteps == 0) {
            this.gameOver = true;
          }
        }
        flipTimer = 0; // Reset the timer
      }
    }
  }

  // Resets the game to its default values with a new shuffled deck
  public void onKeyEvent(String key) {
    if (key.equals("r")) {
      this.board = createBoard();
      this.pairsLeft = 26;
      this.firstSelected = null;
      this.secondSelected = null;
      this.waitingForMatch = false;
      this.score = 26;
      this.totalGuesses = 0;
      this.maxSteps = 100;
      this.gameWon = false;
      this.gameOver = false;
    }
  }
}

// Examples for the Concentration game
class ExamplesConcentration {
  void testConcentrationWorld(Tester t) {
    ArrayList<ArrayList<Card>> board = ConcentrationWorld.createBoard();
    ConcentrationWorld world = new ConcentrationWorld(board);
    world.bigBang(700, 500, 0.03);
  }

  Card redCard1 = new Card("♥", "1");
  Card redCard2 = new Card("♦", "1");
  Card blackCard1 = new Card("♠", "1");
  Card blackCard2 = new Card("♣", "2");

  void testCardFlip(Tester t) {
    t.checkExpect(redCard1.isFaceUp(), false);
    redCard1.flip();
    t.checkExpect(redCard1.isFaceUp(), true);
    redCard1.flip();
    t.checkExpect(redCard1.isFaceUp(), false);
  }

  void testCardGetValue(Tester t) {
    t.checkExpect(redCard1.getValue(), "1");
    t.checkExpect(blackCard2.getValue(), "2");
  }

  void testCardGetSuit(Tester t) {
    t.checkExpect(redCard1.getSuit(), "♥");
    t.checkExpect(blackCard2.getSuit(), "♣");
  }

  void testCardToString(Tester t) {
    redCard1.flip();
    t.checkExpect(redCard1.toString(), "1♥");
  }

  void testCardIsMatch(Tester t) {
    t.checkExpect(redCard1.isMatch(redCard2), true);
    t.checkExpect(redCard1.isMatch(blackCard1), false);
    t.checkExpect(blackCard1.isMatch(blackCard2), false);
  }

  void testCardSuitColor(Tester t) {
    t.checkExpect(redCard1.isRedSuit(), true);
    t.checkExpect(redCard2.isRedSuit(), true);
    t.checkExpect(blackCard1.isBlackSuit(), true);
    t.checkExpect(blackCard2.isBlackSuit(), true);
    t.checkExpect(redCard1.isBlackSuit(), false);
    t.checkExpect(blackCard1.isRedSuit(), false);
  }

  void testCreateBoard(Tester t) {
    ArrayList<ArrayList<Card>> board = ConcentrationWorld.createBoard();
    t.checkExpect(board.size(), 4);
    t.checkExpect(board.get(0).size(), 13);
  }

  void testGameReset(Tester t) {
    ArrayList<ArrayList<Card>> board = ConcentrationWorld.createBoard();
    ConcentrationWorld world = new ConcentrationWorld(board);
    world.onKeyEvent("r");
    t.checkExpect(world.pairsLeft, 26);
    t.checkExpect(world.score, 26);
    t.checkExpect(world.totalGuesses, 0);
    t.checkExpect(world.firstSelected, null);
    t.checkExpect(world.secondSelected, null);
    t.checkExpect(world.gameWon, false);
    t.checkExpect(world.gameOver, false);
  }

  void testMouseClick(Tester t) {
    ArrayList<ArrayList<Card>> board = ConcentrationWorld.createBoard();
    ConcentrationWorld world = new ConcentrationWorld(board);
    Card card = board.get(0).get(0);
    Posn pos = new Posn(50, 50);
    world.onMouseClicked(pos);
    t.checkExpect(card.isFaceUp(), true);
  }

  void testOnTick(Tester t) {
    ArrayList<ArrayList<Card>> board = ConcentrationWorld.createBoard();
    ConcentrationWorld world = new ConcentrationWorld(board);
    world.onTick();
    t.checkExpect(world.elapsedTime, 1);
  }
}
