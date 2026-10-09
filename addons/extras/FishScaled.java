package holylois.boombox;

/** Added to ItemStackRenderState by FishRenderStateMixin: the size a weighed fish is drawn at, and how far it is lifted on the ground. */
public interface FishScaled {
    float holyLois$fishScale();
    float holyLois$fishLift();
    void holyLois$setFish(float scale, float lift);
    void holyLois$tint(int argb);
}
