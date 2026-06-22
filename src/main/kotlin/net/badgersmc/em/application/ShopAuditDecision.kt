package net.badgersmc.em.application

/** Pure audit decision for a single shop (REQ-294). Infra resolves world/block state; this decides. */
object ShopAuditDecision {
    enum class Decision { KEEP, REMOVE, SKIP }

    /**
     * @param worldLoaded     is the shop's container world currently loaded?
     * @param blockIsContainer when the world is loaded, is the container block still a Container?
     * SKIP when the world is unloaded (NEVER delete — we can't see the block). KEEP when the
     * container is present. REMOVE only when the world is loaded and the block is not a container.
     */
    fun evaluate(worldLoaded: Boolean, blockIsContainer: Boolean): Decision =
        if (!worldLoaded) Decision.SKIP else if (blockIsContainer) Decision.KEEP else Decision.REMOVE
}