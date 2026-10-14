import math
import random
import time
import matplotlib.pyplot as plt

totalPoints = int(input())
pointsList = [[random.randint(0, 100), random.randint(0, 100)] for _ in range(totalPoints)]
print("Generated Points:", pointsList)

def getDistance(pointOne, pointTwo):
    return math.dist(pointOne, pointTwo)

startTime = time.perf_counter()
minimumBruteForce, coordinatesBruteForce = float('inf'), []
for indexA in range(totalPoints):
    for indexB in range(indexA + 1, totalPoints):
        if (distanceValue := getDistance(pointsList[indexA], pointsList[indexB])) < minimumBruteForce:
            minimumBruteForce, coordinatesBruteForce = distanceValue, [pointsList[indexA], pointsList[indexB]]
bruteForceTime = (time.perf_counter() - startTime) * 1000

print("\n--- Brute-Force ---")
print(round(minimumBruteForce, 6))
print(coordinatesBruteForce)
print(f"Time taken: {bruteForceTime:.4f} ms")

def bruteForceHelper(subPoints):
    minimumDistance, bestPair = float('inf'), []
    for indexI in range(len(subPoints)):
        for indexJ in range(indexI + 1, len(subPoints)):
            if (distanceValue := getDistance(subPoints[indexI], subPoints[indexJ])) < minimumDistance:
                minimumDistance, bestPair = distanceValue, [subPoints[indexI], subPoints[indexJ]]
    return minimumDistance, bestPair

def divideAndConquer(allPoints):
    sortedByX = sorted(allPoints, key=lambda p: p[0])
    sortedByY = sorted(allPoints, key=lambda p: p[1])
    
    visualizationData = {}

    def closestRecursive(currentPx, currentPy):
        if len(currentPx) <= 3:
            return bruteForceHelper(currentPx)

        middleIndex = len(currentPx) // 2
        middlePoint = currentPx[middleIndex]
        
        leftPx, rightPx = currentPx[:middleIndex], currentPx[middleIndex:]
        leftSet = {tuple(p) for p in leftPx}
        
        leftPy = [p for p in currentPy if tuple(p) in leftSet]
        rightPy = [p for p in currentPy if tuple(p) not in leftSet]

        distanceOne, pairOne = closestRecursive(leftPx, leftPy)
        distanceTwo, pairTwo = closestRecursive(rightPx, rightPy)

        currentMinDistance, bestPair = (distanceOne, pairOne) if distanceOne < distanceTwo else (distanceTwo, pairTwo)
        
        visualizationData['middlePoint'] = middlePoint
        visualizationData['distance'] = currentMinDistance
        
        stripList = [p for p in currentPy if abs(p[0] - middlePoint[0]) < currentMinDistance]
        
        finalMinimumDistance, finalBestPair = currentMinDistance, bestPair
        stripLength = len(stripList)
        for indexI in range(stripLength):
            for indexJ in range(indexI + 1, min(indexI + 8, stripLength)):
                if (stripDistance := getDistance(stripList[indexI], stripList[indexJ])) < finalMinimumDistance:
                    finalMinimumDistance, finalBestPair = stripDistance, [stripList[indexI], stripList[indexJ]]
                    
        return finalMinimumDistance, finalBestPair

    minDist, bestPair = closestRecursive(sortedByX, sortedByY)
    return minDist, bestPair, visualizationData

startTime = time.perf_counter()
minimumDistance, coordinatesDivideAndConquer, vizData = divideAndConquer(pointsList)
divideAndConquerTime = (time.perf_counter() - startTime) * 1000

print("\n--- Divide-and-Conquer ---")
print(round(minimumDistance, 6))
print(coordinatesDivideAndConquer)
print(f"Time taken: {divideAndConquerTime:.4f} ms")

nValues = [10, 30, 50, 100, 150, 200]
bruteForceTimes = []
divideTimes = []

for n in nValues:
    samplePoints = [[random.randint(0, 100), random.randint(0, 100)] for _ in range(n)]
    
    tStart = time.perf_counter()
    minBf = float('inf')
    for i in range(n):
        for j in range(i + 1, n):
            if getDistance(samplePoints[i], samplePoints[j]) < minBf:
                minBf = getDistance(samplePoints[i], samplePoints[j])
    bruteForceTimes.append((time.perf_counter() - tStart) * 1000)
    
    tStart = time.perf_counter()
    divideAndConquer(samplePoints)
    divideTimes.append((time.perf_counter() - tStart) * 1000)

fig, (axisSpatial, axisTime) = plt.subplots(1, 2, figsize=(14, 6))

allX = [p[0] for p in pointsList]
allY = [p[1] for p in pointsList]
axisSpatial.scatter(allX, allY, color='black', label='Points')

if coordinatesBruteForce:
    bfX = [coordinatesBruteForce[0][0], coordinatesBruteForce[1][0]]
    bfY = [coordinatesBruteForce[0][1], coordinatesBruteForce[1][1]]
    axisSpatial.plot(bfX, bfY, color='red', linestyle='--', linewidth=2, label='Brute-Force Closest')
    axisSpatial.scatter(bfX, bfY, color='red', s=80)

if coordinatesDivideAndConquer:
    dcX = [coordinatesDivideAndConquer[0][0], coordinatesDivideAndConquer[1][0]]
    dcY = [coordinatesDivideAndConquer[0][1], coordinatesDivideAndConquer[1][1]]
    axisSpatial.plot(dcX, dcY, color='green', linestyle='-', linewidth=2, label='D&C Closest')
    axisSpatial.scatter(dcX, dcY, color='green', s=80)

if 'middlePoint' in vizData:
    midX = vizData['middlePoint'][0]
    dVal = vizData['distance']
    axisSpatial.axvline(x=midX, color='blue', linestyle=':', label='Dividing Line')
    axisSpatial.axvspan(midX - dVal, midX + dVal, color='blue', alpha=0.15, label='Strip Region')

axisSpatial.set_title('Closest Pair Visualization')
axisSpatial.legend()

axisTime.plot(nValues, bruteForceTimes, marker='o', color='red', label='Brute-Force')
axisTime.plot(nValues, divideTimes, marker='s', color='green', label='Divide-and-Conquer')
axisTime.set_title('Running Time vs N')
axisTime.set_xlabel('Number of Points (N)')
axisTime.set_ylabel('Time (ms)')
axisTime.legend()

plt.tight_layout()
plt.show()