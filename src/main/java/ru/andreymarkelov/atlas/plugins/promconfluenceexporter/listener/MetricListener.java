package ru.andreymarkelov.atlas.plugins.promconfluenceexporter.listener;

import com.atlassian.confluence.event.events.space.SpaceRemoveEvent;
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
    public void afterPropertiesSet() throws Exception {
        eventPublisher.register(this);
    }

    @Override
    public void destroy() throws Exception {
        eventPublisher.unregister(this);
    }

    @EventListener
    public void onSpaceRemoveEvent(SpaceRemoveEvent spaceRemoveEvent) {

    }

    /*
    @EventListener
    public void onDashboardViewEvent(DashboardViewEvent dashboardViewEvent) {
        metricCollector.dashboardViewCounter(dashboardViewEvent.getId(), getCurrentUser());
    }

    @EventListener
    public void onIssueViewEvent(IssueViewEvent issueViewEvent) {
        Issue issue = issueManager.getIssueObject(issueViewEvent.getId());
        if (issue != null) {
            metricCollector.issueViewCounter(issue.getProjectObject().getKey(), issue.getKey(), getCurrentUser());
        }
    }

    @EventListener
    public void onLoginEvent(LoginEvent loginEvent) {
        ApplicationUser applicationUser = loginEvent.getUser();
        metricCollector.userLoginCounter((applicationUser != null) ? applicationUser.getUsername() : "");
    }

    @EventListener
    public void onLogoutEvent(LogoutEvent logoutEvent) {
        ApplicationUser applicationUser = logoutEvent.getUser();
        metricCollector.userLogoutCounter((applicationUser != null) ? applicationUser.getUsername() : "");
    }

    private String getCurrentUser() {
        return jiraAuthenticationContext.isLoggedInUser() ? jiraAuthenticationContext.getLoggedInUser().getName() : "";
    }*/
}
