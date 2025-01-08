import java.util.*;
import tester.*;
import javalib.impworld.*;
import javalib.worldimages.*;
import java.awt.Color;

interface ICollection<T> {
  // add an item to this collection
  void add(T t);

  // remove an item from this collection
  T remove();

  // size of this collection
  int size();
}

class Stack<T> implements ICollection<T> {
  ArrayDeque<T> items;

  Stack() {
    this.items = new ArrayDeque<T>();
  }

  // add an item to this stack
  public void add(T t) {
    this.items.addFirst(t);
  }

  // remove an item to this stack
  public T remove() {
    return this.items.removeFirst();
  }

  // size of this stack
  public int size() {
    return this.items.size();
  }
}

class BridgeItWorld extends World {
  ArrayList<ArrayList<Vertex>> board;
  int size; // This is the size of the grid (If set to 5 the grid would be 5x5)
  boolean p1turn; // This is used to keep track of who's turn it is. (If true then player 1)
  boolean gameOver; // This is used so when a valid path is found we can update the game state

  // Constructor to initialize the world with a grid size.
  BridgeItWorld(int size) {
    // Does a check for whether the size is valid i.e. greater than 3 and is odd.
    if (size < 3 || size % 2 == 0) {
      throw new IllegalArgumentException("Size was either less than 3 or not odd");
    }
    this.size = size;
    this.boardInitialization();
    this.linkCells();
    this.p1turn = true;
    this.gameOver = false;
  }

  // Handles player input as the only input is mouse clicks
  public void onMouseClicked(Posn pos) {
    if (gameOver) {
      return;
    }
    int row = pos.y / 40;
    int col = pos.x / 40;

    if (row >= 0 && row < this.size && col >= 0 && col < this.size) {
      Vertex selected = this.board.get(row).get(col);
      if (selected.color.equals(Color.WHITE)) {
        // Selected color is determined by who's turn it is
        if (p1turn) {
          selected.color = Color.PINK;
        }
        else {
          selected.color = Color.MAGENTA;
        }

        // After we update the vertex we check to see if a winning path has been found
        // if so we then update the game state. Else we switch the players turn
        if (hasPathDFS()) {
          gameOver = true;
        }
        else {
          this.p1turn = !this.p1turn;
        }
      }
    }
  }

  boolean hasPathDFS() {
    // We first determine the target color we are checking paths for based on whos
    // turn it is
    Color targetColor;
    if (p1turn) {
      targetColor = Color.PINK;
    }
    else {
      targetColor = Color.MAGENTA;
    }

    // Check from the left to right for Player 1
    if (targetColor == Color.PINK) {
      for (int row = 0; row < this.size; row++) {
        if (this.board.get(row).get(0).color.equals(targetColor) && dfs(this.board.get(row).get(0),
            this.board.get(row).get(size - 1), targetColor, new Stack<Vertex>())) {
          return true;
        }
      }
    }
    // Check from the top to the bottom for Player 2
    else {
      for (int col = 0; col < this.size; col++) {
        if (this.board.get(0).get(col).color.equals(targetColor) && dfs(this.board.get(0).get(col),
            this.board.get(size - 1).get(col), targetColor, new Stack<Vertex>())) {
          return true;
        }
      }
    }
    return false;
  }

  boolean dfs(Vertex start, Vertex target, Color targetColor, ICollection<Vertex> worklist) {
    ArrayList<Vertex> alreadySeen = new ArrayList<Vertex>();
    worklist.add(start);

    // If we have reached a valid edge return true so the game ends
    if (targetColor == Color.PINK && start.col == this.size - 1) {
      return true;
    }
    if (targetColor == Color.MAGENTA && start.row == this.size - 1) {
      return true;
    }

    while (worklist.size() > 0) {
      Vertex current = worklist.remove();
      if (current == target && current.color.equals(targetColor)) {
        return true;
      }

      // This adds the current vertexs neighbors to the worklist if it exists
      else if (!alreadySeen.contains(current)) {
        if (current.top != null && current.top.color.equals(targetColor)
            && !alreadySeen.contains(current.top)) {
          worklist.add(current.top);
        }
        if (current.bottom != null && current.bottom.color.equals(targetColor)
            && !alreadySeen.contains(current.bottom)) {
          worklist.add(current.bottom);
        }
        if (current.left != null && current.left.color.equals(targetColor)
            && !alreadySeen.contains(current.left)) {
          worklist.add(current.left);
        }
        if (current.right != null && current.right.color.equals(targetColor)
            && !alreadySeen.contains(current.right)) {
          worklist.add(current.right);
        }

        alreadySeen.add(current);
      }
    }
    return false;
  }

  // Initializes the board with cells and assigns colors based on the row and
  // column.
  void boardInitialization() {
    this.board = new ArrayList<>();
    for (int row = 0; row < this.size; row++) {
      ArrayList<Vertex> cell = new ArrayList<>();
      for (int col = 0; col < this.size; col++) {
        Color color;

        // If the Row is odd then we alternate between White and Magenta
        if (row % 2 == 0) {
          // If the column is odd the vertex should be White otherwise we default to
          // Magenta
          if (col % 2 == 0) {
            color = Color.WHITE;
          }
          else {
            color = Color.MAGENTA;
          }
          // If the Row is even then we alternate between Pink and White
        }
        else {
          // If the column is odd the vertex should then be Pink otherwise we default to
          // White
          if (col % 2 == 0) {
            color = Color.PINK;
          }
          else {
            color = Color.WHITE;
          }
        }
        cell.add(new Vertex(row, col, color));
      }
      this.board.add(cell);
    }
  }

  // This method should link the cells/vertexs to their respective neighbors
  void linkCells() {
    for (int row = 0; row < this.size; row++) {
      for (int col = 0; col < this.size; col++) {
        Vertex current = this.board.get(row).get(col);

        if (row > 0) {
          current.top = this.board.get(row - 1).get(col);
        }
        if (row < this.size - 1) {
          current.bottom = this.board.get(row + 1).get(col);
        }
        if (col > 0) {
          current.left = this.board.get(row).get(col - 1);
        }
        if (col < this.size - 1) {
          current.right = this.board.get(row).get(col + 1);
        }
      }
    }
  }

  // Creates the scene and then renders the gameboard
  public WorldScene makeScene() {
    WorldScene scene = new WorldScene(this.size * 40, this.size * 40);

    for (ArrayList<Vertex> row : this.board) {
      for (Vertex cell : row) {
        scene.placeImageXY(cell.draw(), cell.col * 40 + 20, cell.row * 40 + 20);
      }
    }

    // If the game is over display who won.
    if (gameOver && p1turn) {
      scene.placeImageXY(new TextImage("Player one has won!", 20, Color.BLACK),
          (this.size * 40) / 2, (this.size * 40) / 2);
    }
    else if (gameOver && !p1turn) {
      scene.placeImageXY(new TextImage("Player two has won!", 20, Color.BLACK),
          (this.size * 40) / 2, (this.size * 40) / 2);
    }
    return scene;
  }
}

class Vertex {
  int row; // The row of the cell in the grid.
  int col; // The column of the cell in the grid.
  Color color; // The color of the cell.
  Vertex top; // Reference to the top neighbor.
  Vertex bottom; // Reference to the bottom neighbor.
  Vertex right; // Reference to the right neighbor.
  Vertex left; // Reference to the left neighbor.

  Vertex(int row, int col, Color color) {
    this.row = row;
    this.col = col;
    this.color = color;
  }

  WorldImage draw() {
    return new RectangleImage(40, 40, OutlineMode.SOLID, this.color);

  }
}

class ExamplesBridgeIt {

  void testBridgeItWorld(Tester t) {
    BridgeItWorld world = new BridgeItWorld(9);
    world.bigBang(750, 750, 0.1);
  }

  void testDraw(Tester t) {
    // This creates a vertex at 0,0 with the color white. Then we check to make sure
    // the proper
    // rectangle got drawn.
    Vertex vertex1 = new Vertex(0, 0, Color.WHITE);
    t.checkExpect(vertex1.draw(), new RectangleImage(40, 40, OutlineMode.SOLID, Color.WHITE));

    // This creates a vertex at 1,0 with the color white. Then we check to make sure
    // the proper
    // rectangle got drawn.
    Vertex vertex2 = new Vertex(1, 0, Color.MAGENTA);
    t.checkExpect(vertex2.draw(), new RectangleImage(40, 40, OutlineMode.SOLID, Color.MAGENTA));

    // This creates a vertex at 1,0 with the color pink. Then we check to make sure
    // the proper
    // rectangle got drawn.
    Vertex vertex3 = new Vertex(1, 0, Color.PINK);
    t.checkExpect(vertex3.draw(), new RectangleImage(40, 40, OutlineMode.SOLID, Color.PINK));
  }

  void testLinkCells(Tester t) {
    BridgeItWorld world = new BridgeItWorld(5);
    world.linkCells();

    ArrayList<ArrayList<Vertex>> board = world.board;

    // Checks a cell that has a neighbor in the right and bottom
    Vertex cell = board.get(0).get(0);
    t.checkExpect(cell.right, board.get(0).get(1));
    t.checkExpect(cell.bottom, board.get(1).get(0));

    // Checks a cell that has a neighbor in only the left and botoom
    cell = board.get(0).get(4);
    t.checkExpect(cell.left, board.get(0).get(3));
    t.checkExpect(cell.bottom, board.get(1).get(4));

    // Checks a cell that has a neighbor in only the top and right
    cell = board.get(4).get(0);
    t.checkExpect(cell.top, board.get(3).get(0));
    t.checkExpect(cell.right, board.get(4).get(1));

    // Checks a cell that has a neighbor in only the top and left
    cell = board.get(4).get(4);
    t.checkExpect(cell.top, board.get(3).get(4));
    t.checkExpect(cell.left, board.get(4).get(3));

    // Checks a cell that has a neighbor in all directions
    cell = board.get(2).get(2);
    t.checkExpect(cell.top, board.get(1).get(2));
    t.checkExpect(cell.bottom, board.get(3).get(2));
    t.checkExpect(cell.right, board.get(2).get(3));
    t.checkExpect(cell.left, board.get(2).get(1));

  }

  void testBoardInitialization(Tester t) {
    // Creates a world with a size of 5 (A 5x5 grid)
    BridgeItWorld world = new BridgeItWorld(5);
    ArrayList<ArrayList<Vertex>> board = world.board;

    // This checks to make sure the board size is actually 5 as inputted by checking
    // amount of rows and columns
    t.checkExpect(board.size(), 5);
    t.checkExpect(board.get(0).size(), 5);

    // This checks to make sure the first cell at 0,0 is actually white
    t.checkExpect(board.get(0).get(0).color, Color.WHITE);

    // This checks to make sure the second cell (0,1) should be magenta as this
    // should always be magenta and should always exist no matter the grid size
    t.checkExpect(board.get(0).get(1).color, Color.MAGENTA);

    // This checks to make sure the first cell of the second row (1,0) should be
    // pink as this should always be pink and should always exist no matter the grid
    // size
    t.checkExpect(board.get(1).get(0).color, Color.PINK);

    // This checks to make sure the second cell of the second row at 1,1 is white as
    // this should always be white and always exist
    t.checkExpect(board.get(1).get(1).color, Color.WHITE);
  }

  void testHasPathDFS(Tester t) {
    // Since the grid is only 3x3 by clicking in the middle the game ends
    // So this is for player 1 winning
    BridgeItWorld world = new BridgeItWorld(3);
    world.boardInitialization();
    world.linkCells();
    world.onMouseClicked(new Posn(60, 60));
    t.checkExpect(world.hasPathDFS(), true);

    // This is for the case where player 2 wins
    BridgeItWorld world2 = new BridgeItWorld(3);
    world2.boardInitialization();
    world2.linkCells();
    world2.onMouseClicked(new Posn(0, 0));
    t.checkExpect(world2.p1turn, false);
    world2.onMouseClicked(new Posn(60, 60));
    t.checkExpect(world2.hasPathDFS(), true);
  }

  void testOnMouseClicked(Tester t) {
    BridgeItWorld world = new BridgeItWorld(5);

    t.checkExpect(world.p1turn, true);
    world.onMouseClicked(new Posn(0, 0));
    t.checkExpect(world.board.get(0).get(0).color, Color.PINK);

    // After a click the player turn should swap
    t.checkExpect(world.p1turn, false);
    world.onMouseClicked(new Posn(60, 60));
    t.checkExpect(world.board.get(0).get(1).color, Color.MAGENTA);

    t.checkExpect(world.p1turn, true);
    world.onMouseClicked(new Posn(0, 0));
    // A click on something that has already been clicked should remain the same
    // players turn
    t.checkExpect(world.p1turn, true);
  }

  void testGameOverCondition(Tester t) {
    BridgeItWorld world = new BridgeItWorld(3);
    world.boardInitialization();
    world.linkCells();

    // Since the grid is only 3x3 by clicking in the middle the game ends as a valid
    // path is formed
    // for either player
    world.onMouseClicked(new Posn(60, 60));

    t.checkExpect(world.gameOver, true);
    t.checkExpect(world.p1turn, true);
  }

}
