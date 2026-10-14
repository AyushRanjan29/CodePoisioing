import argparse
import heapq
import math
import random
import re
import sys

import matplotlib
import matplotlib.pyplot as plt
from matplotlib.animation import FuncAnimation, PillowWriter
from matplotlib.patches import Circle

start_color = "#2ECC40"
end_color = "#E02020"
route_color = "#1E5AA8"
coupon_color = "#F28C00"
idle_color = "#9A9A9A"
city_radius = 0.11

def makegraph(totalcities, totalflights, randomgen):
    totalflights = min(totalflights, totalcities * (totalcities - 1))
    flightlist = []
    usedroutes = set()
    while len(flightlist) < totalflights:
        source = randomgen.randint(1, totalcities)
        target = randomgen.randint(1, totalcities)
        if source == target or (source, target) in usedroutes:
            continue
        usedroutes.add((source, target))
        flightlist.append((source, target, randomgen.randint(1, 100)))
    return flightlist

def readgraph(filepath):
    flightlist = []
    with open(filepath) as file_obj:
        for line in file_obj:
            numbers = re.findall(r"\d+", line)
            if len(numbers) == 3:
                flightlist.append(tuple(map(int, numbers)))
    return flightlist

def cheapestroute(totalcities, flightlist, maxstops, maxcoupons):
    flightgraph = {i: [] for i in range(1, totalcities + 1)}
    for source, target, price in flightlist:
        flightgraph[source].append((target, price))

    statenodes = [(1, 0, 0, 0, False, -1)]
    priorityqueue = [(0, 0)]
    visitedstates = set()
    
    while priorityqueue:
        currentcost, nodeindex = heapq.heappop(priorityqueue)
        currentcity, flightstaken, couponsused, _, _, _ = statenodes[nodeindex]
        
        if (currentcity, flightstaken, couponsused) in visitedstates:
            continue
        visitedstates.add((currentcity, flightstaken, couponsused))

        if currentcity == totalcities:
            routelegs = []
            while statenodes[nodeindex][5] != -1:
                nextcity, _, _, ticketprice, couponapplied, parentindex = statenodes[nodeindex]
                routelegs.append((statenodes[parentindex][0], nextcity, ticketprice, couponapplied))
                nodeindex = parentindex
            return currentcost, routelegs[::-1]
            
        if flightstaken == maxstops + 1:
            continue

        for nextcity, ticketprice in flightgraph[currentcity]:
            statenodes.append((nextcity, flightstaken + 1, couponsused, ticketprice, False, nodeindex))
            heapq.heappush(priorityqueue, (currentcost + ticketprice, len(statenodes) - 1))
            if couponsused < maxcoupons:
                statenodes.append((nextcity, flightstaken + 1, couponsused + 1, ticketprice, True, nodeindex))
                heapq.heappush(priorityqueue, (currentcost + ticketprice // 2, len(statenodes) - 1))
                
    return -1, None

class flightmap:
    def __init__(self, totalcities, flightlist, routelegs, maxstops, maxcoupons, totalcost):
        self.totalcities = totalcities
        self.flightlist = flightlist
        self.routelegs = routelegs
        self.maxstops = maxstops
        self.maxcoupons = maxcoupons
        self.totalcost = totalcost
        
        self.citypositions = {i: (math.sin(2 * math.pi * (i - 1) / totalcities), math.cos(2 * math.pi * (i - 1) / totalcities))
                    for i in range(1, totalcities + 1)}
        self.legindexmap = {(source, target): i for i, (source, target, _, _) in enumerate(routelegs)}
        self.figure = plt.figure(figsize=(11, 6.2))
        self.mapaxes = self.figure.add_axes([0.02, 0.04, 0.62, 0.86])
        self.infoaxes = self.figure.add_axes([0.66, 0.04, 0.32, 0.86])
        self.figure.suptitle(f"Cheapest flights: minimum cost ${totalcost}   (K = {maxstops} stops, C = {maxcoupons} coupons)",
                          fontsize=13, fontweight="bold")

    def createcurve(self, citya, cityb, bendradius=0.22):
        (x_start, y_start) = self.citypositions[citya]
        (x_end, y_end) = self.citypositions[cityb]
        distance = math.hypot(x_end - x_start, y_end - y_start)
        unit_x, unit_y = (x_end - x_start) / distance, (y_end - y_start) / distance
        point0 = (x_start + unit_x * city_radius, y_start + unit_y * city_radius)
        point1 = (x_end - unit_x * city_radius, y_end - unit_y * city_radius)
        center_x = (point0[0] + point1[0]) / 2 + bendradius * (point1[1] - point0[1])
        center_y = (point0[1] + point1[1]) / 2 - bendradius * (point1[0] - point0[0])

        def getpoint(timeparam):
            return ((1 - timeparam) ** 2 * point0[0] + 2 * (1 - timeparam) * timeparam * center_x + timeparam * timeparam * point1[0],
                    (1 - timeparam) ** 2 * point0[1] + 2 * (1 - timeparam) * timeparam * center_y + timeparam * timeparam * point1[1])
        return getpoint

    def drawflightline(self, citya, cityb, linecolor, linewidth, progresslimit=1.0):
        curvefunc = self.createcurve(citya, cityb)
        curvepoints = [curvefunc(progresslimit * step / 30) for step in range(31)]
        self.mapaxes.plot([point[0] for point in curvepoints], [point[1] for point in curvepoints], color=linecolor, lw=linewidth,
                     solid_capstyle="round", zorder=2)
        if progresslimit >= 1.0:
            self.mapaxes.annotate("", xy=curvefunc(1.0), xytext=curvefunc(0.9), zorder=2,
                             arrowprops=dict(arrowstyle="-|>", color=linecolor, lw=linewidth, mutation_scale=14,
                                             shrinkA=0, shrinkB=0))

    def drawframe(self, completedlegs, flightprogress):
        currentaxes = self.mapaxes
        currentlegs = self.routelegs
        currentaxes.clear()
        currentaxes.set_xlim(-1.35, 1.35)
        currentaxes.set_ylim(-1.3, 1.3)
        currentaxes.set_aspect("equal")
        currentaxes.axis("off")

        for source, target, _ in self.flightlist:
            if self.legindexmap.get((source, target), 10 ** 6) >= completedlegs:
                self.drawflightline(source, target, idle_color, 1.3)
                
        for legindex in range(completedlegs):
            source, target, _, couponapplied = currentlegs[legindex]
            self.drawflightline(source, target, coupon_color if couponapplied else route_color, 3.5)
            
        planeposition = None
        if completedlegs < len(currentlegs):
            source, target, _, couponapplied = currentlegs[completedlegs]
            if flightprogress > 0:
                self.drawflightline(source, target, coupon_color if couponapplied else route_color, 3.5, progresslimit=flightprogress)
                planeposition = self.createcurve(source, target)(flightprogress)

        for source, target, ticketprice in self.flightlist:
            label_x, label_y = self.createcurve(source, target)(0.4 if source < target else 0.6)
            legindex = self.legindexmap.get((source, target), 10 ** 6)
            if legindex < completedlegs and currentlegs[legindex][3]:
                labeltext, textcolor, textweight = f"${ticketprice} > ${ticketprice // 2}", coupon_color, "bold"
            elif legindex < completedlegs:
                labeltext, textcolor, textweight = f"${ticketprice}", route_color, "bold"
            else:
                labeltext, textcolor, textweight = f"${ticketprice}", "#555555", "normal"
            currentaxes.text(label_x, label_y, labeltext, ha="center", va="center", fontsize=8.5, color=textcolor, fontweight=textweight,
                    zorder=4, bbox=dict(boxstyle="round,pad=0.15", fc="white", ec="none", alpha=0.9))

        for cityindex, (city_x, city_y) in self.citypositions.items():
            circlefill = start_color if cityindex == 1 else end_color if cityindex == self.totalcities else "white"
            currentaxes.add_patch(Circle((city_x, city_y), city_radius, fc=circlefill, ec="#555555" if circlefill == "white" else circlefill,
                                lw=2, zorder=5))
            currentaxes.text(city_x, city_y, str(cityindex), ha="center", va="center", fontsize=13, fontweight="bold",
                    color="black" if circlefill == "white" else "white", zorder=6)

        if currentlegs and planeposition is None:
            currentcity = currentlegs[completedlegs][0] if completedlegs < len(currentlegs) else currentlegs[-1][1]
            planeposition = (self.citypositions[currentcity][0] + city_radius * 0.8, self.citypositions[currentcity][1] + city_radius * 0.8)
        if planeposition:
            currentaxes.add_patch(Circle(planeposition, 0.035, fc="black", ec="white", lw=1.2, zorder=7))

        self.drawinfo(completedlegs)

    def drawinfo(self, completedlegs):
        infopanel = self.infoaxes
        infopanel.clear()
        infopanel.set_xlim(0, 1)
        infopanel.set_ylim(0, 1)
        infopanel.axis("off")

        totalspent = sum(legdata[2] // 2 if legdata[3] else legdata[2] for legdata in self.routelegs[:completedlegs])
        totalusedcoupons = sum(1 for legdata in self.routelegs[:completedlegs] if legdata[3])

        infopanel.text(0.02, 0.95, "Coupons", fontsize=12, fontweight="bold", va="center")
        for couponindex in range(self.maxcoupons):
            coupon_x, coupon_y = 0.06 + (couponindex % 6) * 0.15, 0.88 - (couponindex // 6) * 0.09
            infopanel.scatter([coupon_x], [coupon_y], s=650, color="#DDDDDD" if couponindex < totalusedcoupons else coupon_color, zorder=3)
            infopanel.text(coupon_x, coupon_y, "1/2", ha="center", va="center", fontsize=8, fontweight="bold",
                      color="#AAAAAA" if couponindex < totalusedcoupons else "white", zorder=4)

        text_y_pos = 0.88 - ((max(self.maxcoupons, 1) - 1) // 6) * 0.09 - 0.12
        infopanel.text(0.02, text_y_pos, f"Total cost: ${totalspent}", fontsize=12, fontweight="bold", va="center")
        infopanel.text(0.02, text_y_pos - 0.06, f"Flights: {completedlegs} of {len(self.routelegs)}   (limit {self.maxstops + 1})",
                  fontsize=10, va="center")
        infopanel.text(0.02, text_y_pos - 0.11, f"Stops allowed: {self.maxstops}", fontsize=10, va="center")

        text_y_pos -= 0.20
        infopanel.text(0.02, text_y_pos, "Flights taken", fontsize=12, fontweight="bold", va="center")
        for legindex in range(completedlegs):
            source, target, ticketprice, couponapplied = self.routelegs[legindex]
            legtext = f"{source} > {target}:  ${ticketprice}" + (f" > ${ticketprice // 2}  (coupon)" if couponapplied else "")
            infopanel.text(0.02, text_y_pos - 0.06 - legindex * 0.055, legtext, fontsize=10, va="center",
                      color=coupon_color if couponapplied else route_color)

def buildframes(totallegs, flightsteps=20, pauseduration=8):
    animationframes = [(0, 0)] * pauseduration
    for legindex in range(totallegs):
        animationframes += [(legindex, (step + 1) / flightsteps) for step in range(flightsteps)]
        animationframes += [(legindex + 1, 0)] * pauseduration
    return animationframes + [(totallegs, 0)] * pauseduration * 2

def runmain():
    argparser = argparse.ArgumentParser()
    argparser.add_argument("--n", type=int)
    argparser.add_argument("--m", type=int, default=10)
    argparser.add_argument("--seed", type=int)
    argparser.add_argument("--graph")
    argparser.add_argument("--save")
    parsedargs = argparser.parse_args()

    maxstops, maxcoupons = map(int, sys.stdin.readline().split())

    if parsedargs.graph:
        flightlist = readgraph(parsedargs.graph)
        totalcities = parsedargs.n or max(max(source, target) for source, target, _ in flightlist)
        totalcost, routelegs = cheapestroute(totalcities, flightlist, maxstops, maxcoupons)
    else:
        totalcities = parsedargs.n or 6
        randomgen = random.Random(parsedargs.seed)
        flightlist = makegraph(totalcities, parsedargs.m, randomgen)
        totalcost, routelegs = cheapestroute(totalcities, flightlist, maxstops, maxcoupons)
        while totalcost == -1:
            flightlist = makegraph(totalcities, parsedargs.m, randomgen)
            totalcost, routelegs = cheapestroute(totalcities, flightlist, maxstops, maxcoupons)

    print(totalcost)
    if totalcost == -1:
        return

    mapviz = flightmap(totalcities, flightlist, routelegs, maxstops, maxcoupons, totalcost)
    animationframes = buildframes(len(routelegs))
    mapanimation = FuncAnimation(mapviz.figure, lambda frameindex: mapviz.drawframe(*animationframes[frameindex]), frames=len(animationframes),
                         interval=40, repeat=True)

    savetarget = parsedargs.save
    if not savetarget and matplotlib.get_backend().lower() == "agg":
        savetarget = "flights.gif"
        print("No display available, saving the animation to flights.gif instead.", file=sys.stderr)
    if savetarget:
        mapanimation.save(savetarget, writer=PillowWriter(fps=25))
        print("Saved", savetarget, file=sys.stderr)
    else:
        plt.show()

if __name__ == "__main__":
    runmain()