package com.campusstore.core.service;

import com.campusstore.core.domain.event.ConflictException;
import com.campusstore.core.domain.event.ResourceNotFoundException;
import com.campusstore.infrastructure.persistence.entity.DepartmentEntity;
import com.campusstore.infrastructure.persistence.repository.DepartmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DepartmentService {

    private static final Logger log = LoggerFactory.getLogger(DepartmentService.class);

    private final DepartmentRepository departmentRepository;
    private final AuditService auditService;

    public DepartmentService(DepartmentRepository departmentRepository, AuditService auditService) {
        this.departmentRepository = departmentRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<DepartmentEntity> listAll() {
        return departmentRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Page<DepartmentEntity> listPaged(Pageable pageable) {
        return departmentRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public DepartmentEntity getById(Long id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department", id));
    }

    @Transactional
    public DepartmentEntity create(String name, String description, Long actorUserId) {
        if (departmentRepository.existsByName(name)) {
            throw new ConflictException("Department with name '" + name + "' already exists");
        }

        DepartmentEntity dept = new DepartmentEntity();
        dept.setName(name);
        dept.setDescription(description);
        DepartmentEntity saved = departmentRepository.save(dept);

        log.info("Department created: {} (id={})", name, saved.getId());
        auditService.log(actorUserId, "DEPARTMENT.CREATE", "Department", saved.getId(),
                "{\"name\":\"" + name + "\"}");

        return saved;
    }
}
