package com.cgi.eoss.fstep.api.controllers;

import com.cgi.eoss.fstep.security.FstepPermission;
import com.cgi.eoss.fstep.security.FstepSecurityService;
import com.cgi.eoss.fstep.model.Databasket;
import com.cgi.eoss.fstep.model.FstepFile;
import com.cgi.eoss.fstep.model.FstepService;
import com.cgi.eoss.fstep.model.Group;
import com.cgi.eoss.fstep.model.Job;
import com.cgi.eoss.fstep.model.JobConfig;
import com.cgi.eoss.fstep.model.Project;
import com.cgi.eoss.fstep.model.User;
import com.cgi.eoss.fstep.persistence.service.GroupDataService;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jooq.lambda.Seq;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.acls.domain.GrantedAuthoritySid;
import org.springframework.security.acls.domain.ObjectIdentityImpl;
import org.springframework.security.acls.domain.PrincipalSid;
import org.springframework.security.acls.model.AccessControlEntry;
import org.springframework.security.acls.model.Acl;
import org.springframework.security.acls.model.MutableAcl;
import org.springframework.security.acls.model.NotFoundException;
import org.springframework.security.acls.model.ObjectIdentity;
import org.springframework.security.acls.model.Sid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * <p>A {@link RestController} for updating and modifying ACLs. There is no direct model mapping, so this is not a
 * {@link org.springframework.data.rest.core.annotation.RepositoryRestResource}.</p>
 */
@RestController
@RequestMapping("/acls")
@Log4j2
public class AclsApi {

    /**
     * <p>Collate a collection of {@link AccessControlEntry}s into a set of permissions, and transform that set into its
     * corresponding {@link FstepPermission}.</p>
     */
    private static final Collector<AccessControlEntry, ?, FstepPermission> SPRING_FSTEP_ACL_SET_COLLECTOR =
            Collectors.collectingAndThen(Collectors.mapping(AccessControlEntry::getPermission, Collectors.toSet()), FstepPermission::getFstepPermission);

    private final FstepSecurityService fstepSecurityService;
    private final GroupDataService groupDataService;

    @Autowired
    public AclsApi(FstepSecurityService fstepSecurityService, GroupDataService groupDataService) {
        this.fstepSecurityService = fstepSecurityService;
        this.groupDataService = groupDataService;
    }

    @PostMapping("/databasket/{databasketId}")
    @PreAuthorize("hasAnyRole('CONTENT_AUTHORITY', 'ADMIN') or hasPermission(#databasket, 'administration')")
    public void setDatabasketAcl(@ModelAttribute("databasketId") Databasket databasket, @RequestBody FstepAccessControlList acl) {
        Preconditions.checkArgument(databasket.getId().equals(acl.getEntityId()), "ACL subject entity ID mismatch: URL %s vs BODY %s", databasket.getId(), acl.getEntityId());
        setAcl(new ObjectIdentityImpl(Databasket.class, databasket.getId()), databasket.getOwner(), acl.getPermissions());
    }

    @GetMapping("/databasket/{databasketId}")
    @PreAuthorize("hasAnyRole('CONTENT_AUTHORITY', 'ADMIN') or hasPermission(#databasket, 'administration')")
    public FstepAccessControlList getDatabasketAcls(@ModelAttribute("databasketId") Databasket databasket) {
        return FstepAccessControlList.builder()
                .entityId(databasket.getId())
                .permissions(getFstepPermissions(new ObjectIdentityImpl(Databasket.class, databasket.getId())))
                .build();
    }

    @PostMapping("/fstepFile/{fstepFileId}")
    @PreAuthorize("hasAnyRole('CONTENT_AUTHORITY', 'ADMIN') or hasPermission(#fstepFile, 'administration')")
    public void setFstepFileAcl(@ModelAttribute("fstepFileId") FstepFile fstepFile, @RequestBody FstepAccessControlList acl) {
        Preconditions.checkArgument(fstepFile.getId().equals(acl.getEntityId()), "ACL subject entity ID mismatch: URL %s vs BODY %s", fstepFile.getId(), acl.getEntityId());
        setAcl(new ObjectIdentityImpl(FstepFile.class, fstepFile.getId()), fstepFile.getOwner(), acl.getPermissions());
    }

    @GetMapping("/fstepFile/{fstepFileId}")
    @PreAuthorize("hasAnyRole('CONTENT_AUTHORITY', 'ADMIN') or hasPermission(#fstepFile, 'administration')")
    public FstepAccessControlList getFstepFileAcls(@ModelAttribute("fstepFileId") FstepFile fstepFile) {
        return FstepAccessControlList.builder()
                .entityId(fstepFile.getId())
                .permissions(getFstepPermissions(new ObjectIdentityImpl(FstepFile.class, fstepFile.getId())))
                .build();
    }

    @PostMapping("/group/{groupId}")
    @PreAuthorize("hasAnyRole('CONTENT_AUTHORITY', 'ADMIN') or hasPermission(#group, 'administration')")
    public void setGroupAcl(@ModelAttribute("groupId") Group group, @RequestBody FstepAccessControlList acl) {
        Preconditions.checkArgument(group.getId().equals(acl.getEntityId()), "ACL subject entity ID mismatch: URL %s vs BODY %s", group.getId(), acl.getEntityId());
        setAcl(new ObjectIdentityImpl(Group.class, group.getId()), group.getOwner(), acl.getPermissions());
    }

    @GetMapping("/group/{groupId}")
    @PreAuthorize("hasAnyRole('CONTENT_AUTHORITY', 'ADMIN') or hasPermission(#group, 'administration')")
    public FstepAccessControlList getGroupAcls(@ModelAttribute("groupId") Group group) {
        return FstepAccessControlList.builder()
                .entityId(group.getId())
                .permissions(getFstepPermissions(new ObjectIdentityImpl(Group.class, group.getId())))
                .build();
    }

    @PostMapping("/job/{jobId}")
    @PreAuthorize("hasAnyRole('CONTENT_AUTHORITY', 'ADMIN') or hasPermission(#job, 'administration')")
    public void setJobAcl(@ModelAttribute("jobId") Job job, @RequestBody FstepAccessControlList acl) {
        Preconditions.checkArgument(job.getId().equals(acl.getEntityId()), "ACL subject entity ID mismatch: URL %s vs BODY %s", job.getId(), acl.getEntityId());
        setAcl(new ObjectIdentityImpl(Job.class, job.getId()), job.getOwner(), acl.getPermissions());
    }

    @GetMapping("/job/{jobId}")
    @PreAuthorize("hasAnyRole('CONTENT_AUTHORITY', 'ADMIN') or hasPermission(#job, 'administration')")
    public FstepAccessControlList getJobAcls(@ModelAttribute("jobId") Job job) {
        return FstepAccessControlList.builder()
                .entityId(job.getId())
                .permissions(getFstepPermissions(new ObjectIdentityImpl(Job.class, job.getId())))
                .build();
    }

    @PostMapping("/jobConfig/{jobConfigId}")
    @PreAuthorize("hasAnyRole('CONTENT_AUTHORITY', 'ADMIN') or hasPermission(#jobConfig, 'administration')")
    public void setJobConfigAcl(@ModelAttribute("jobConfigId") JobConfig jobConfig, @RequestBody FstepAccessControlList acl) {
        Preconditions.checkArgument(jobConfig.getId().equals(acl.getEntityId()), "ACL subject entity ID mismatch: URL %s vs BODY %s", jobConfig.getId(), acl.getEntityId());
        setAcl(new ObjectIdentityImpl(JobConfig.class, jobConfig.getId()), jobConfig.getOwner(), acl.getPermissions());
    }

    @GetMapping("/jobConfig/{jobConfigId}")
    @PreAuthorize("hasAnyRole('CONTENT_AUTHORITY', 'ADMIN') or hasPermission(#jobConfig, 'administration')")
    public FstepAccessControlList getJobConfigAcls(@ModelAttribute("jobConfigId") JobConfig jobConfig) {
        return FstepAccessControlList.builder()
                .entityId(jobConfig.getId())
                .permissions(getFstepPermissions(new ObjectIdentityImpl(JobConfig.class, jobConfig.getId())))
                .build();
    }

    @PostMapping("/project/{projectId}")
    @PreAuthorize("hasAnyRole('CONTENT_AUTHORITY', 'ADMIN') or hasPermission(#project, 'administration')")
    public void setProjectAcl(@ModelAttribute("projectId") Project project, @RequestBody FstepAccessControlList acl) {
        Preconditions.checkArgument(project.getId().equals(acl.getEntityId()), "ACL subject entity ID mismatch: URL {} vs BODY {}", project.getId(), acl.getEntityId());
        setAcl(new ObjectIdentityImpl(Project.class, project.getId()), project.getOwner(), acl.getPermissions());
    }

    @GetMapping("/project/{projectId}")
    @PreAuthorize("hasAnyRole('CONTENT_AUTHORITY', 'ADMIN') or hasPermission(#project, 'administration')")
    public FstepAccessControlList getProjectAcls(@ModelAttribute("projectId") Project project) {
        return FstepAccessControlList.builder()
                .entityId(project.getId())
                .permissions(getFstepPermissions(new ObjectIdentityImpl(Project.class, project.getId())))
                .build();
    }

    @PostMapping("/service/{serviceId}")
    @PreAuthorize("hasAnyRole('CONTENT_AUTHORITY', 'ADMIN') or hasPermission(#service, 'administration')")
    public void setServiceAcl(@ModelAttribute("serviceId") FstepService service, @RequestBody FstepAccessControlList acl) {
        Preconditions.checkArgument(service.getId().equals(acl.getEntityId()), "ACL subject entity ID mismatch: URL %s vs BODY %s", service.getId(), acl.getEntityId());
        setAcl(new ObjectIdentityImpl(FstepService.class, service.getId()), service.getOwner(), acl.getPermissions());
    }

    @GetMapping("/service/{serviceId}")
    @PreAuthorize("hasAnyRole('CONTENT_AUTHORITY', 'ADMIN') or hasPermission(#service, 'administration')")
    public FstepAccessControlList getServiceAcls(@ModelAttribute("serviceId") FstepService service) {
        return FstepAccessControlList.builder()
                .entityId(service.getId())
                .permissions(getFstepPermissions(new ObjectIdentityImpl(FstepService.class, service.getId())))
                .build();
    }

    private List<FstepAccessControlEntry> getFstepPermissions(ObjectIdentity objectIdentity) {
        try {
            Acl acl = fstepSecurityService.getAcl(objectIdentity);

            return acl.getEntries().stream()
                    .filter(ace -> ace.getSid() instanceof GrantedAuthoritySid && ((GrantedAuthoritySid) ace.getSid()).getGrantedAuthority().startsWith("GROUP_"))
                    .collect(Collectors.groupingBy(this::getGroup, SPRING_FSTEP_ACL_SET_COLLECTOR))
                    .entrySet().stream()
                    .map(e -> FstepAccessControlEntry.builder().group(new SGroup(e.getKey())).permission(e.getValue()).build())
                    .collect(Collectors.toList());
        } catch (NotFoundException e) {
            LOG.debug("No ACLs present for object {}", objectIdentity);
            return ImmutableList.of();
        }
    }

    private Group getGroup(AccessControlEntry ace) {
        return groupDataService.getById(Long.parseLong(((GrantedAuthoritySid) ace.getSid()).getGrantedAuthority().replaceFirst("^GROUP_", "")));
    }

    private Group hydrateSGroup(SGroup sGroup) {
        return groupDataService.getById(sGroup.getId());
    }

    private void setAcl(ObjectIdentity objectIdentity, User owner, List<FstepAccessControlEntry> newAces) {
        LOG.debug("Creating ACL on object {}: {}", objectIdentity, newAces);

        MutableAcl acl = fstepSecurityService.getAcl(objectIdentity);
        boolean published = fstepSecurityService.isPublic(objectIdentity);

        // JdbcMutableAclService#saveAcl deletes the entire list before saving
        // So we have to reset the entire desired permission list for the group

        // First delete all existing ACEs in reverse order...
        int aceCount = acl.getEntries().size();
        Seq.range(0, aceCount - 1).reverse().forEach(acl::deleteAce);

        // ... then ensure the owner ACE is present (always ADMIN)
        if (owner != null) {
            Sid ownerSid = new PrincipalSid(owner.getName());
            FstepPermission.ADMIN.getAclPermissions()
                    .forEach(p -> acl.insertAce(acl.getEntries().size(), p, ownerSid, true));
        }

        // ...then insert the new ACEs
        newAces.forEach((ace) -> {
            Sid sid = new GrantedAuthoritySid(hydrateSGroup(ace.getGroup()));
            ace.getPermission().getAclPermissions()
                    .forEach(p -> acl.insertAce(acl.getEntries().size(), p, sid, true));
        });

        fstepSecurityService.saveAcl(acl);

        // ... and finally re-publish if necessary
        if (published) {
            fstepSecurityService.publish(objectIdentity);
        }
    }

    @Data
    @NoArgsConstructor
    private static final class FstepAccessControlList {
        private Long entityId;
        private List<FstepAccessControlEntry> permissions;

        @Builder
        public FstepAccessControlList(Long entityId, List<FstepAccessControlEntry> permissions) {
            this.entityId = entityId;
            this.permissions = permissions;
        }
    }

    @Data
    @NoArgsConstructor
    private static final class FstepAccessControlEntry {
        private SGroup group;
        private FstepPermission permission;

        @Builder
        public FstepAccessControlEntry(SGroup group, FstepPermission permission) {
            this.group = group;
            this.permission = permission;
        }
    }

    @Data
    @NoArgsConstructor
    private static final class SGroup {
        private Long id;
        private String name;

        private SGroup(Group group) {
            this.id = group.getId();
            this.name = group.getName();
        }
    }

}
