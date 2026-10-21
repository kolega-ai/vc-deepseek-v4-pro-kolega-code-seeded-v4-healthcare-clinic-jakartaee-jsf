package com.oakfield.clinic.channel.web.common;

import com.oakfield.clinic.decision.AccessDeniedException;
import com.oakfield.clinic.decision.NotAuthenticatedException;
import jakarta.faces.FacesException;
import jakarta.faces.application.ViewExpiredException;
import jakarta.faces.context.ExceptionHandler;
import jakarta.faces.context.ExceptionHandlerFactory;
import jakarta.faces.context.ExceptionHandlerWrapper;
import jakarta.faces.context.FacesContext;
import jakarta.faces.event.ExceptionQueuedEvent;
import jakarta.faces.event.ExceptionQueuedEventContext;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.util.Iterator;

/**
 * Central exception handling for the JSF views: authorization failures render
 * the styled 403 page, authentication failures return to login, and
 * unexpected errors log server-side while showing a generic error page.
 */
public class AppExceptionHandlerFactory extends ExceptionHandlerFactory {

    public AppExceptionHandlerFactory(ExceptionHandlerFactory wrapped) {
        super(wrapped);
    }

    @Override
    public ExceptionHandler getExceptionHandler() {
        return new AppExceptionHandler(getWrapped().getExceptionHandler());
    }

    private static class AppExceptionHandler extends ExceptionHandlerWrapper {

        private static final Logger LOG = Logger.getLogger(AppExceptionHandler.class);

        private final ExceptionHandler wrapped;

        AppExceptionHandler(ExceptionHandler wrapped) {
            this.wrapped = wrapped;
        }

        @Override
        public ExceptionHandler getWrapped() {
            return wrapped;
        }

        @Override
        public void handle() throws FacesException {
            Iterator<ExceptionQueuedEvent> events = getUnhandledExceptionQueuedEvents().iterator();
            boolean handled = false;
            while (events.hasNext()) {
                ExceptionQueuedEvent event = events.next();
                ExceptionQueuedEventContext eventContext = event.getContext();
                FacesContext faces = eventContext.getContext();
                Throwable cause = findKnownCause(eventContext.getException());
                try {
                    if (cause instanceof ViewExpiredException || cause instanceof NotAuthenticatedException) {
                        faces.getExternalContext().redirect(
                                faces.getExternalContext().getRequestContextPath() + "/login.xhtml?expired=true");
                        handled = true;
                    } else if (cause instanceof AccessDeniedException) {
                        faces.getExternalContext().redirect(
                                faces.getExternalContext().getRequestContextPath() + "/403.xhtml");
                        handled = true;
                    } else {
                        LOG.errorf("Unhandled view error: %s", eventContext.getException().toString());
                    }
                    events.remove();
                } catch (IOException e) {
                    LOG.errorf("Could not redirect on view error: %s", e.getMessage());
                }
            }
            if (!handled) {
                wrapped.handle();
            }
        }

        /** Walk the entire cause chain (FacesException -> EJBException -> domain exception). */
        private static Throwable findKnownCause(Throwable t) {
            Throwable cur = t;
            while (cur != null) {
                if (cur instanceof ViewExpiredException
                        || cur instanceof NotAuthenticatedException
                        || cur instanceof AccessDeniedException) {
                    return cur;
                }
                Throwable next = cur.getCause();
                if (next == cur) return cur;
                cur = next;
            }
            return t;
        }

        @Override
        public boolean isListenerForSource(Object source) {
            return wrapped.isListenerForSource(source);
        }

        @Override
        public void processEvent(jakarta.faces.event.SystemEvent event) throws jakarta.faces.event.AbortProcessingException {
            wrapped.processEvent(event);
        }
    }
}
