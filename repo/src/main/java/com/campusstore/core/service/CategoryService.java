package com.campusstore.core.service;

import com.campusstore.core.domain.event.ConflictException;
import com.campusstore.core.domain.event.ResourceNotFoundException;
import com.campusstore.infrastructure.persistence.entity.CategoryEntity;
import com.campusstore.infrastructure.persistence.repository.CategoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryService {

    private static final Logger log = LoggerFactory.getLogger(CategoryService.class);

    private final CategoryRepository categoryRepository;
    private final AuditService auditService;

    public CategoryService(CategoryRepository categoryRepository, AuditService auditService) {
        this.categoryRepository = categoryRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<CategoryEntity> listAll() {
        return categoryRepository.findAll();
    }

    @Transactional(readOnly = true)
    public CategoryEntity getById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
    }

    @Transactional
    public CategoryEntity create(String name, String description, Long parentId,
                                  Long actorUserId) {
        if (categoryRepository.existsByName(name)) {
            throw new ConflictException("Category with name '" + name + "' already exists");
        }

        CategoryEntity cat = new CategoryEntity();
        cat.setName(name);
        cat.setDescription(description);
        if (parentId != null) {
            // The scalar `parent_id` column is insertable=false/updatable=false.
            // Persistence happens via the @ManyToOne `parent` relationship.
            CategoryEntity parent = categoryRepository.findById(parentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Parent category", parentId));
            cat.setParent(parent);
        }

        CategoryEntity saved = categoryRepository.save(cat);
        log.info("Category created: {} (id={})", name, saved.getId());
        auditService.log(actorUserId, "CATEGORY.CREATE", "Category", saved.getId(),
                "{\"name\":\"" + name + "\"}");

        return saved;
    }
}
