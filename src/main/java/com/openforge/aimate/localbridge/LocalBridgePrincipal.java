package com.openforge.aimate.localbridge;

/** Security principal for local-bridge bearer auth. */
public record LocalBridgePrincipal(Long userAgentId, Long ownerUserId) {}
