package com.campusstore.core.port.inbound;

import java.util.List;

/**
 * Inbound port for department operations.
 */
public interface DepartmentUseCase {

    /**
     * List all departments.
     *
     * @return list of department views
     */
    List<DepartmentView> listDepartments();

    /**
     * Create a new department.
     *
     * @param name        the department name
     * @param description the department description
     * @return the id of the created department
     */
    Long createDepartment(String name, String description);

    // ── View types ─────────────────────────────────────────────────────

    record DepartmentView(
            Long id,
            String name,
            String description
    ) {
    }
}
