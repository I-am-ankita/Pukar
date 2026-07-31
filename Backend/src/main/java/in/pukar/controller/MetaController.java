package in.pukar.controller;

import in.pukar.common.ApiResponse;
import in.pukar.entity.ComplaintPriority;
import in.pukar.entity.ComplaintStatus;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/meta")
public class MetaController {

    private record Category(String code, String label) {}

    private static final List<Category> CATEGORIES = List.of(
            new Category("BRIBERY", "Bribery / Extortion"),
            new Category("CORRUPTION", "Corruption / Misuse of office"),
            new Category("LAND_ENCROACHMENT", "Land Encroachment"),
            new Category("WATER", "Water Supply"),
            new Category("SANITATION", "Sanitation / Sewage"),
            new Category("GARBAGE", "Garbage / Solid Waste"),
            new Category("ROAD", "Roads / Potholes"),
            new Category("ELECTRICITY", "Electricity / Streetlights"),
            new Category("OTHER", "Other Grievance")
    );

    @GetMapping("/categories")
    public ApiResponse<List<Category>> categories() {
        return ApiResponse.success(CATEGORIES);
    }

    @GetMapping("/statuses")
    public ApiResponse<List<String>> statuses() {
        return ApiResponse.success(Arrays.stream(ComplaintStatus.values()).map(Enum::name).toList());
    }

    @GetMapping("/priorities")
    public ApiResponse<List<String>> priorities() {
        return ApiResponse.success(Arrays.stream(ComplaintPriority.values()).map(Enum::name).toList());
    }
}
