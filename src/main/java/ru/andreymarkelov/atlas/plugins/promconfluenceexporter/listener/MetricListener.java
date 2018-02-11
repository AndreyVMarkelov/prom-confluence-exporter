package ru.andreymarkelov.atlas.plugins.promconfluenceexporter.listener;

import com.atlassian.confluence.cluster.safety.ClusterPanicEvent;
import com.atlassian.confluence.event.events.cluster.ClusterReindexRequiredEvent;
import com.atlassian.confluence.event.events.label.LabelAddEvent;
import com.atlassian.confluence.event.events.label.LabelCreateEvent;
import com.atlassian.confluence.event.events.label.LabelDeleteEvent;
import com.atlassian.confluence.event.events.label.LabelRemoveEvent;
import com.atlassian.confluence.event.events.security.LoginEvent;
import com.atlassian.confluence.event.events.security.LogoutEvent;
import com.atlassian.confluence.event.events.space.SpaceCreateEvent;
import com.atlassian.confluence.event.events.space.SpaceRemoveEvent;
import com.atlassian.confluence.labels.Labelable;
import com.atlassian.confluence.labels.Namespace;
import com.atlassian.confluence.pages.AbstractPage;
import com.atlassian.confluence.pages.Page;
import com.atlassian.confluence.user.AuthenticatedUserThreadLocal;
import com.atlassian.event.api.EventListener;
import com.atlassian.event.api.EventPublisher;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import ru.andreymarkelov.atlas.plugins.promconfluenceexporter.manager.MetricCollector;

public class MetricListener implements InitializingBean, DisposableBean {
    private final EventPublisher eventPublisher;
    private final MetricCollector metricCollector;

    public MetricListener(
            EventPublisher eventPublisher,
            MetricCollector metricCollector) {
        this.eventPublisher = eventPublisher;
        this.metricCollector = metricCollector;
    }

    @Override
    public void afterPropertiesSet() {
        eventPublisher.register(this);
    }

    @Override
    public void destroy() {
        eventPublisher.unregister(this);
    }

    //--> Cluster
    @EventListener
    public void onClusterPanicEvent(ClusterPanicEvent clusterPanicEvent) {
        metricCollector.clusterPanicCounter();
    }

    // Labels
    @EventListener
    public void onLabelAddEvent(LabelAddEvent labelAddEvent) {
        if (labelAddEvent.getLabelled() instanceof AbstractPage) {
            Labelable labelled = labelAddEvent.getLabelled();
            Namespace namespace = labelAddEvent.getLabel().getNamespace();
            String visibility = namespace != null ? namespace.getVisibility() : "";
            String prefix = namespace != null ? namespace.getPrefix() : "";
            String source = labelled instanceof Page ? "page" : "blogpost";
            metricCollector.labelAddCounter(visibility, prefix, source, ((AbstractPage) labelled).getSpaceKey());
        }
    }

    @EventListener
    public void onLabelCreateEvent(LabelCreateEvent labelCreateEvent) {
        Namespace namespace = labelCreateEvent.getLabel().getNamespace();
        String visibility = namespace != null ? namespace.getVisibility() : "";
        String prefix = namespace != null ? namespace.getPrefix() : "";
        metricCollector.labelCreateCounter(visibility, prefix);
    }

    @EventListener
    public void onLabelDeleteEvent(LabelDeleteEvent labelDeleteEvent) {
        Namespace namespace = labelDeleteEvent.getLabel().getNamespace();
        String visibility = namespace != null ? namespace.getVisibility() : "";
        String prefix = namespace != null ? namespace.getPrefix() : "";
        metricCollector.labelDeleteCounter(visibility, prefix);
    }

    @EventListener
    public void onLabelRemoveEvent(LabelRemoveEvent labelRemoveEvent) {
        if (labelRemoveEvent.getLabelled() instanceof AbstractPage) {
            Labelable labelled = labelRemoveEvent.getLabelled();
            Namespace namespace = labelRemoveEvent.getLabel().getNamespace();
            String visibility = namespace != null ? namespace.getVisibility() : "";
            String prefix = namespace != null ? namespace.getPrefix() : "";
            String source = labelled instanceof Page ? "page" : "blogpost";
            metricCollector.labelRemoveCounter(visibility, prefix, source, ((AbstractPage) labelled).getSpaceKey());
        }
    }

    // Reindex
    @EventListener
    public void onClusterReindexRequiredEvent(ClusterReindexRequiredEvent clusterReindexRequiredEvent) {

    }

    // Space
    @EventListener
    public void onSpaceRemoveEvent(SpaceRemoveEvent spaceRemoveEvent) {
        String username = AuthenticatedUserThreadLocal.getUsername();
        if (username == null) {
            username = "";
        }
        metricCollector.spaceDeleteCounter(username);
    }

    @EventListener
    public void onSpaceCreateEvent(SpaceCreateEvent spaceCreateEvent) {
        String username = AuthenticatedUserThreadLocal.getUsername();
        if (username == null) {
            username = "";
        }
        metricCollector.spaceCreateCounter(username);
    }

    //--> Login/Logout

    @EventListener
    public void onLoginEvent(LoginEvent loginEvent) {
        String username = loginEvent.getUsername();
        if (username == null) {
            username = "";
        }
        String ip = loginEvent.getRemoteIP();
        if (ip == null) {
            ip = "";
        }
        metricCollector.userLoginCounter(username, ip);
    }

    @EventListener
    public void onLogoutEvent(LogoutEvent logoutEvent) {
        String username = logoutEvent.getUsername();
        if (username == null) {
            username = "";
        }
        String ip = logoutEvent.getRemoteIP();
        if (ip == null) {
            ip = "";
        }
        metricCollector.userLogoutCounter(username, ip);
    }
}
