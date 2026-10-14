import java.util.*;

public class Q1 {

    static class playerstate {
        int row, col, collectedkeys, stepstaken;

        playerstate(int row, int col, int collectedkeys, int stepstaken) {
            this.row = row;
            this.col = col;
            this.collectedkeys = collectedkeys;
            this.stepstaken = stepstaken;
        }
    }

    static char[][] createmaze(int size, int totalkeys, Random randomgenerator) {
        char[][] maze = new char[size][size];
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                maze[row][col] = randomgenerator.nextInt(100) < 27 ? '#' : '.';
            }
        }

        List<Integer> availablepositions = new ArrayList<>();
        for (int i = 0; i < size * size; i++) {
            availablepositions.add(i);
        }
        Collections.shuffle(availablepositions, randomgenerator);

        String elements = "S@";
        for (int i = 0; i < totalkeys; i++) {
            elements += "" + (char) ('a' + i) + (char) ('A' + i);
        }

        for (int index = 0; index < elements.length(); index++) {
            int position = availablepositions.get(index);
            maze[position / size][position % size] = elements.charAt(index);
        }

        return maze;
    }

    static int calculateshortestpath(char[][] maze) {
        int size = maze.length;
        int startrow = 0;
        int startcol = 0;

        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                if (maze[row][col] == 'S') {
                    startrow = row;
                    startcol = col;
                }
            }
        }

        Queue<playerstate> statequeue = new LinkedList<>();
        Set<String> visitedstates = new HashSet<>();

        statequeue.add(new playerstate(startrow, startcol, 0, 0));
        visitedstates.add(startrow + "," + startcol + ",0");

        int[] rowmovements = { 1, -1, 0, 0 };
        int[] colmovements = { 0, 0, 1, -1 };

        while (!statequeue.isEmpty()) {
            playerstate current = statequeue.poll();

            if (maze[current.row][current.col] == '@') {
                return current.stepstaken;
            }

            for (int direction = 0; direction < 4; direction++) {
                int nextrow = current.row + rowmovements[direction];
                int nextcol = current.col + colmovements[direction];

                if (nextrow < 0 || nextcol < 0 || nextrow >= size || nextcol >= size) {
                    continue;
                }

                char element = maze[nextrow][nextcol];
                if (element == '#') {
                    continue;
                }

                int keysheld = current.collectedkeys;

                if (element >= 'a' && element <= 'f') {
                    keysheld = keysheld | (1 << (element - 'a'));
                }

                if (element >= 'A' && element <= 'F') {
                    boolean hasmatchingkey = (keysheld & (1 << (element - 'A'))) != 0;
                    if (!hasmatchingkey) {
                        continue;
                    }
                }

                String statekey = nextrow + "," + nextcol + "," + keysheld;
                if (!visitedstates.contains(statekey)) {
                    visitedstates.add(statekey);
                    statequeue.add(new playerstate(nextrow, nextcol, keysheld, current.stepstaken + 1));
                }
            }
        }
        return -1;
    }

    public static void main(String[] args) {
        Scanner input = new Scanner(System.in);
        int totalkeys = input.nextInt();

        int mazesize = args.length > 0 ? Integer.parseInt(args[0]) : 10;
        Random randomgenerator = args.length > 1 ? new Random(Long.parseLong(args[1])) : new Random();

        char[][] maze = createmaze(mazesize, totalkeys, randomgenerator);
        while (calculateshortestpath(maze) == -1) {
            maze = createmaze(mazesize, totalkeys, randomgenerator);
        }

        for (char[] row : maze) {
            for (char element : row) {
                System.err.print(element == '@' ? 'E' : element);
            }
            System.err.println();
        }

        System.out.println(calculateshortestpath(maze));
    }
}