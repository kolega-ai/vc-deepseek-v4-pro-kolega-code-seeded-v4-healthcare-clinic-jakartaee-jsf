package com.oakfield.clinic.channel.web.staff;

import com.oakfield.clinic.channel.web.common.SessionInfo;
import com.oakfield.clinic.model.Patient;
import com.oakfield.clinic.model.Provider;
import com.oakfield.clinic.svc.PageResult;
import com.oakfield.clinic.svc.PatientService;
import com.oakfield.clinic.svc.ProviderService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.util.List;

/**
 * Staff patient directory: query/provider/active filters, whitelisted sort
 * ("name" | "created" | "dob"), and paging.
 */
@Named("staffPatients")
@ViewScoped
public class StaffPatientsBean implements Serializable {

    @Inject
    private SessionInfo session;
    @Inject
    private PatientService patients;
    @Inject
    private ProviderService providers;

    private String query;
    private Long providerId;
    private boolean activeOnly = true;
    private String sort = "name";
    private int pageIndex;
    private PageResult<Patient> page;

    @PostConstruct
    public void load() {
        refresh();
    }

    private void refresh() {
        page = patients.search(session.ctx(),
                new PatientService.PatientFilter(query, providerId, activeOnly),
                pageIndex, 20, sort);
    }

    public void filter() {
        pageIndex = 0;
        refresh();
    }

    public void clear() {
        query = null;
        providerId = null;
        activeOnly = true;
        sort = "name";
        pageIndex = 0;
        refresh();
    }

    public void sortByName() {
        sort = "name";
        filter();
    }

    public void sortByCreated() {
        sort = "created";
        filter();
    }

    public void sortByDob() {
        sort = "dob";
        filter();
    }

    public void nextPage() {
        pageIndex++;
        refresh();
    }

    public void prevPage() {
        if (pageIndex > 0) {
            pageIndex--;
            refresh();
        }
    }

    public List<Provider> getProviders() {
        return providers.listActive();
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public Long getProviderId() {
        return providerId;
    }

    public void setProviderId(Long providerId) {
        this.providerId = providerId;
    }

    public boolean isActiveOnly() {
        return activeOnly;
    }

    public void setActiveOnly(boolean activeOnly) {
        this.activeOnly = activeOnly;
    }

    public String getSort() {
        return sort;
    }

    public void setSort(String sort) {
        this.sort = sort;
    }

    public int getPageIndex() {
        return pageIndex;
    }

    public void setPageIndex(int pageIndex) {
        this.pageIndex = pageIndex;
    }

    public PageResult<Patient> getPage() {
        return page;
    }
}
