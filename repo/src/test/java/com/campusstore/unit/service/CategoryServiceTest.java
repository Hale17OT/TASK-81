package com.campusstore.unit.service;

import com.campusstore.core.domain.event.ConflictException;
import com.campusstore.core.domain.event.ResourceNotFoundException;
import com.campusstore.core.service.AuditService;
import com.campusstore.core.service.CategoryService;
import com.campusstore.infrastructure.persistence.entity.CategoryEntity;
import com.campusstore.infrastructure.persistence.repository.CategoryRepository;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CategoryService} covering duplicate-name detection,
 * not-found handling, and audit trail invocation on create.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock private CategoryRepository categoryRepository;
    @Mock private AuditService auditService;

    @InjectMocks
    private CategoryService categoryService;

    // ── create ────────────────────────────────────────────────────────

    @Test
    void create_duplicateName_throwsConflictException() {
        when(categoryRepository.existsByName("Electronics")).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> categoryService.create("Electronics", "desc", null, 1L));
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void create_validInput_persists_andAudits() {
        when(categoryRepository.existsByName("Science Kits")).thenReturn(false);
        CategoryEntity saved = new CategoryEntity();
        saved.setId(42L);
        saved.setName("Science Kits");
        when(categoryRepository.save(any(CategoryEntity.class))).thenReturn(saved);

        CategoryEntity result = categoryService.create("Science Kits", "STEM materials", null, 10L);

        assertEquals(42L, result.getId());
        assertEquals("Science Kits", result.getName());
        verify(categoryRepository).save(any(CategoryEntity.class));
        verify(auditService).log(eq(10L), eq("CATEGORY.CREATE"), eq("Category"), eq(42L), anyString());
    }

    @Test
    void create_withParentId_loadsParent() {
        when(categoryRepository.existsByName("Sub")).thenReturn(false);
        CategoryEntity parent = new CategoryEntity();
        parent.setId(1L);
        parent.setName("Parent");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(parent));
        CategoryEntity saved = new CategoryEntity();
        saved.setId(2L);
        saved.setName("Sub");
        when(categoryRepository.save(any(CategoryEntity.class))).thenReturn(saved);

        CategoryEntity result = categoryService.create("Sub", "desc", 1L, 10L);

        assertNotNull(result);
        verify(categoryRepository).findById(1L);
    }

    @Test
    void create_withNonExistentParentId_throwsResourceNotFoundException() {
        when(categoryRepository.existsByName("Sub")).thenReturn(false);
        when(categoryRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> categoryService.create("Sub", "desc", 999L, 10L));
    }

    // ── getById ───────────────────────────────────────────────────────

    @Test
    void getById_notFound_throwsResourceNotFoundException() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> categoryService.getById(99L));
    }

    @Test
    void getById_found_returnsEntity() {
        CategoryEntity cat = new CategoryEntity();
        cat.setId(5L);
        cat.setName("Lab Supplies");
        when(categoryRepository.findById(5L)).thenReturn(Optional.of(cat));

        CategoryEntity result = categoryService.getById(5L);

        assertEquals(5L, result.getId());
        assertEquals("Lab Supplies", result.getName());
    }

    // ── listAll ───────────────────────────────────────────────────────

    @Test
    void listAll_delegatesToRepository() {
        CategoryEntity c1 = new CategoryEntity();
        c1.setId(1L);
        CategoryEntity c2 = new CategoryEntity();
        c2.setId(2L);
        when(categoryRepository.findAll()).thenReturn(List.of(c1, c2));

        List<CategoryEntity> result = categoryService.listAll();

        assertEquals(2, result.size());
    }
}
