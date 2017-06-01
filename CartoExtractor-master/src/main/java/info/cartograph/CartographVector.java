package info.cartograph;

/**
 * @author Shilad Sen
 */
public class CartographVector {
    private final String name;
    private final String id;
    private final String[] linkIds;
    private final float[] vector;
    private final double popularity;

    public CartographVector(String name, String id, String[] linkIds, float[] vector, double popularity) {
        this.name = name;
        this.id = id;
        this.linkIds = linkIds;
        this.vector = vector;
        this.popularity = popularity;
    }

    public String getName() {
        return name;
    }

    public String getId() {
        return id;
    }

    public String[] getLinkIds() {
        return linkIds;
    }

    public float[] getVector() {
        return vector;
    }

    public double getPopularity() {
        return popularity;
    }
}
