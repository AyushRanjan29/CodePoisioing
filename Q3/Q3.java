import java.util.*;

public class Q3 {

    static class flight {
        int origin, destination, price;

        flight(int origin, int destination, int price) {
            this.origin = origin;
            this.destination = destination;
            this.price = price;
        }
    }

    static class state {
        int city, flightcount, couponcount, cost;

        state(int city, int flightcount, int couponcount, int cost) {
            this.city = city;
            this.flightcount = flightcount;
            this.couponcount = couponcount;
            this.cost = cost;
        }
    }

    static List<flight> makegraph(int totalcities, int totalflights, Random randomgen) {
        totalflights = Math.min(totalflights, totalcities * (totalcities - 1));
        List<flight> flightlist = new ArrayList<>();
        Set<String> usedroutes = new HashSet<>();

        while (flightlist.size() < totalflights) {
            int source = 1 + randomgen.nextInt(totalcities);
            int target = 1 + randomgen.nextInt(totalcities);

            if (source == target || usedroutes.contains(source + "," + target)) {
                continue;
            }

            usedroutes.add(source + "," + target);
            flightlist.add(new flight(source, target, 1 + randomgen.nextInt(100)));
        }
        return flightlist;
    }

    static int cheapest(int totalcities, List<flight> flightlist, int maxstops, int maxcoupons) {
        List<List<flight>> flightgraph = new ArrayList<>();
        for (int index = 0; index <= totalcities; index++) {
            flightgraph.add(new ArrayList<>());
        }
        for (flight currentflight : flightlist) {
            flightgraph.get(currentflight.origin).add(currentflight);
        }

        PriorityQueue<state> priorityqueue = new PriorityQueue<>((statea, stateb) -> statea.cost - stateb.cost);
        Set<String> visitedstates = new HashSet<>();
        priorityqueue.add(new state(1, 0, 0, 0));

        while (!priorityqueue.isEmpty()) {
            state currentstate = priorityqueue.poll();

            String stateid = currentstate.city + "," + currentstate.flightcount + "," + currentstate.couponcount;
            if (visitedstates.contains(stateid)) {
                continue;
            }
            visitedstates.add(stateid);

            if (currentstate.city == totalcities) {
                return currentstate.cost;
            }
            if (currentstate.flightcount == maxstops + 1) {
                continue;
            }

            for (flight nextflight : flightgraph.get(currentstate.city)) {
                priorityqueue.add(new state(nextflight.destination, currentstate.flightcount + 1,
                        currentstate.couponcount, currentstate.cost + nextflight.price));

                if (currentstate.couponcount < maxcoupons) {
                    priorityqueue.add(new state(nextflight.destination, currentstate.flightcount + 1,
                            currentstate.couponcount + 1, currentstate.cost + nextflight.price / 2));
                }
            }
        }
        return -1;
    }

    public static void main(String[] args) {
        Scanner inputscanner = new Scanner(System.in);
        int maxstops = inputscanner.nextInt();
        int maxcoupons = inputscanner.nextInt();

        int totalcities = args.length > 0 ? Integer.parseInt(args[0]) : 6;
        int totalflights = args.length > 1 ? Integer.parseInt(args[1]) : 10;
        Random randomgen = args.length > 2 ? new Random(Long.parseLong(args[2])) : new Random();

        List<flight> flightlist = makegraph(totalcities, totalflights, randomgen);
        while (cheapest(totalcities, flightlist, maxstops, maxcoupons) == -1) {
            flightlist = makegraph(totalcities, totalflights, randomgen);
        }

        for (flight currentflight : flightlist) {
            System.err.println(currentflight.origin + " -> " + currentflight.destination + " : " + currentflight.price);
        }
        System.out.println(cheapest(totalcities, flightlist, maxstops, maxcoupons));
    }
}