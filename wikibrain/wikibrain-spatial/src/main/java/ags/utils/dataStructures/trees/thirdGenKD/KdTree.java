package ags.utils.dataStructures.trees.thirdGenKD;

import ags.utils.dataStructures.BinaryHeap;
import ags.utils.dataStructures.MaxHeap;
import ags.utils.dataStructures.MinHeap;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 *
 */
public class KdTree<T> extends KdNode<T> {
    public KdTree(int dimensions) {
        this(dimensions, 24);
    }

    public KdTree(int dimensions, int bucketCapacity) {
        super(dimensions, bucketCapacity);
    }

    public NearestNeighborIterator<T> getNearestNeighborIterator(double[] searchPoint, int maxPointsReturned, DistanceFunction distanceFunction) {
        return new NearestNeighborIterator<T>(this, searchPoint, maxPointsReturned, distanceFunction);
    }

    public MaxHeap<T> findNearestNeighbors(double[] searchPoint, int maxPointsReturned, DistanceFunction distanceFunction) {
        BinaryHeap.Min<KdNode<T>> pendingPaths = new BinaryHeap.Min<KdNode<T>>();
        BinaryHeap.Max<T> evaluatedPoints = new BinaryHeap.Max<T>();
        int pointsRemaining = Math.min(maxPointsReturned, size());
        pendingPaths.offer(0, this);

        while (pendingPaths.size() > 0 && (evaluatedPoints.size() < pointsRemaining || (pendingPaths.getMinKey() < evaluatedPoints.getMaxKey()))) {
            nearestNeighborSearchStep(pendingPaths, evaluatedPoints, pointsRemaining, distanceFunction, searchPoint);
        }

        return evaluatedPoints;
    }

    public List<T> findInBounds(double [] minBound, double [] maxBound) {
        if (minBound.length != dimensions || maxBound.length != dimensions) {
            throw new IllegalArgumentException();
        }
        for (int i = 0; i < minBound.length; i++) {
            if (minBound[i] > maxBound[i]) {
                throw new IllegalArgumentException();
            }
        }

        List<T> results = new ArrayList<T>();

        // Give up if there is no hope of overlap
        if (!this.isLeaf()) {
            for (int i = 0; i < dimensions; i++) {
                if (!overlaps(minBound[i], maxBound[i], this.minBound[i], this.maxBound[i])) {
                    return results;
                }
            }
        }

        List<KdNode<T>> queue = new LinkedList<KdNode<T>>();
        queue.add(this);

        while (!queue.isEmpty()) {
            KdNode<T> node = queue.remove(0);

            // Add matching points
            for (int i = 0; i < node.size(); i++) {
                if (inBounds(minBound, maxBound, node.points[i])) {
                    results.add((T) node.data[i]);
                }
            }

            // Descend if necessary
            int d = node.splitDimension;
            if (node.left != null && overlaps(node.minBound[d], node.splitValue, minBound[d], maxBound[d])) {
                queue.add(node.left);
            }
            if (node.right != null && overlaps(node.splitValue, node.maxBound[d], minBound[d], maxBound[d])) {
                queue.add(node.right);
            }
        }

        return results;
    }

    private boolean inBounds(double [] minPoint, double [] maxPoints, double [] candidate) {
        for (int i = 0; i < candidate.length; i++) {
            if (candidate[i] < minPoint[i] || candidate[i] > maxPoints[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean overlaps(double minA, double maxA, double minB, double maxB) {
        return (minA <= maxB) && (minB <= maxA);
    }

    @SuppressWarnings("unchecked")
    protected static <T> void nearestNeighborSearchStep (
            MinHeap<KdNode<T>> pendingPaths, MaxHeap<T> evaluatedPoints, int desiredPoints,
            DistanceFunction distanceFunction, double[] searchPoint) {
        // If there are pending paths possibly closer than the nearest evaluated point, check it out
        KdNode<T> cursor = pendingPaths.getMin();
        pendingPaths.removeMin();

        // Descend the tree, recording paths not taken
        while (!cursor.isLeaf()) {
            KdNode<T> pathNotTaken;
            if (searchPoint[cursor.splitDimension] > cursor.splitValue) {
                pathNotTaken = cursor.left;
                cursor = cursor.right;
            }
            else {
                pathNotTaken = cursor.right;
                cursor = cursor.left;
            }
            double otherDistance = distanceFunction.distanceToRect(searchPoint, pathNotTaken.minBound, pathNotTaken.maxBound);
            // Only add a path if we either need more points or it's closer than furthest point on list so far
            if (evaluatedPoints.size() < desiredPoints || otherDistance <= evaluatedPoints.getMaxKey()) {
                pendingPaths.offer(otherDistance, pathNotTaken);
            }
        }

        if (cursor.singlePoint) {
            double nodeDistance = distanceFunction.distance(cursor.points[0], searchPoint);
            // Only add a point if either need more points or it's closer than furthest on list so far
            if (evaluatedPoints.size() < desiredPoints || nodeDistance <= evaluatedPoints.getMaxKey()) {
                for (int i = 0; i < cursor.size(); i++) {
                    T value = (T) cursor.data[i];

                    // If we don't need any more, replace max
                    if (evaluatedPoints.size() == desiredPoints) {
                        evaluatedPoints.replaceMax(nodeDistance, value);
                    } else {
                        evaluatedPoints.offer(nodeDistance, value);
                    }
                }
            }
        } else {
            // Add the points at the cursor
            for (int i = 0; i < cursor.size(); i++) {
                double[] point = cursor.points[i];
                T value = (T) cursor.data[i];
                double distance = distanceFunction.distance(point, searchPoint);
                // Only add a point if either need more points or it's closer than furthest on list so far
                if (evaluatedPoints.size() < desiredPoints) {
                    evaluatedPoints.offer(distance, value);
                } else if (distance < evaluatedPoints.getMaxKey()) {
                    evaluatedPoints.replaceMax(distance, value);
                }
            }
        }
    }
}
