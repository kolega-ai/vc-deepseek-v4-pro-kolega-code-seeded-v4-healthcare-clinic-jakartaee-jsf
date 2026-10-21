package com.oakfield.clinic.channel.rs;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import java.util.Set;

/** JAX-RS application for the JSON integration/API surface (explicit class list). */
@ApplicationPath("/api")
public class ApplicationConfig extends Application {

    @Override
    public Set<Class<?>> getClasses() {
        return Set.of(
                AuthResource.class,
                AvailabilityResource.class,
                AppointmentApiResource.class,
                ReferenceResource.class,
                ExportApiResource.class,
                WebhookEchoResource.class,
                WebhookSubscriptionResource.class,
                ReferralDirectoryResource.class,
                PatientApiResource.class,
                DocumentIntakeResource.class,
                HealthResource.class,
                PortalCorsFilter.class,
                StaffingMessageResource.class,
                ReleaseLedgerResource.class,
                LabCardResource.class,
                LabInterfaceResource.class,
                CareAssistResource.class,
                ApiExceptionMapper.class);
    }
}
