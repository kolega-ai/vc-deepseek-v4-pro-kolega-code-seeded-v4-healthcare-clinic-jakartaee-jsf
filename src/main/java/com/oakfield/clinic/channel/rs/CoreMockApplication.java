package com.oakfield.clinic.channel.rs;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import java.util.Set;

/**
 * Vendor-core mock: the pharmacy clearinghouse / payer stub. It runs as its
 * own network endpoint with its own credential (X-Core-Key) and its own error
 * codes (CORE-xxx). Every call must carry a scope token minted by the decision
 * tier; core-mock verifies the signature and enforces the scope claim.
 */
@ApplicationPath("/core")
public class CoreMockApplication extends Application {

    @Override
    public Set<Class<?>> getClasses() {
        return Set.of(CoreMockResource.class);
    }
}
