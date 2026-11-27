package pt.arrumo.management;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import pt.arrumo.domain.LotRepo;
import pt.arrumo.domain.Product;
import pt.arrumo.domain.ProductRepo;
import pt.arrumo.errors.ApiError;
import pt.arrumo.map.Dtos.MapChange;
import pt.arrumo.security.Audit;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    public record ProductDto(long id, String code, String description, String gtin, boolean active) {
        static ProductDto from(Product p) { return new ProductDto(p.getId(), p.getCode(), p.getDescription(), p.getGtin(), p.isActive()); }
    }

    public record ProductRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{1,20}", message = "só letras, números, ponto, hífen (até 20)") String code,
            @NotBlank @Size(max = 200) String description,
            @Pattern(regexp = "(\\d{8}|\\d{12,14})?", message = "deve ter 8, 12, 13 ou 14 dígitos") String gtin,
            Boolean active) {}

    private final ProductRepo products;
    private final LotRepo lots;
    private final Audit audit;
    private final ApplicationEventPublisher events;

    public ProductController(ProductRepo products, LotRepo lots, Audit audit, ApplicationEventPublisher events) {
        this.products = products;
        this.lots = lots;
        this.audit = audit;
        this.events = events;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()") // logistics users need it too, to fill in pallets
    public List<ProductDto> list() {
        return products.findAllByOrderByCode().stream().map(ProductDto::from).toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    @Transactional
    public ProductDto create(@Valid @RequestBody ProductRequest p) {
        String code = p.code().strip();
        if (products.findByCode(code).isPresent()) throw ApiError.conflict("Já existe o produto " + code + ".");
        Product newProduct = new Product(code, p.description().strip());
        applyGtin(newProduct, p.gtin());
        products.save(newProduct);
        audit.write("PRODUCT_CREATED", code + " - " + newProduct.getDescription());
        events.publishEvent(MapChange.reload(Audit.currentUser())); // a new GTIN can fill in pallets
        return ProductDto.from(newProduct);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    @Transactional
    public ProductDto update(@PathVariable long id, @Valid @RequestBody ProductRequest p) {
        Product prod = products.findById(id).orElseThrow(() -> ApiError.notFound("Produto não encontrado."));
        String code = p.code().strip();
        products.findByCode(code).filter(o -> !o.getId().equals(id))
                .ifPresent(o -> { throw ApiError.conflict("Já existe o produto " + code + "."); });
        String before = prod.getCode() + " - " + prod.getDescription() + (prod.isActive() ? "" : " (inactive)");
        prod.setCode(code);
        prod.setDescription(p.description().strip());
        applyGtin(prod, p.gtin());
        if (p.active() != null) prod.setActive(p.active());
        audit.write("PRODUCT_UPDATED", before + " -> " + code + " - " + prod.getDescription() + (prod.isActive() ? "" : " (inactive)"));
        events.publishEvent(MapChange.reload(Audit.currentUser()));
        return ProductDto.from(prod);
    }

    /** Only products without lots can be deleted. With history, deactivate it (hidden from lists, old lots keep the name). */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable long id) {
        Product prod = products.findById(id).orElseThrow(() -> ApiError.notFound("Produto não encontrado."));
        if (lots.existsByProduct(prod))
            throw ApiError.conflict("O produto " + prod.getCode() + " tem lotes registados. Desativa-o em vez de o apagar.");
        products.delete(prod);
        audit.write("PRODUCT_DELETED", prod.getCode() + " - " + prod.getDescription());
        return ResponseEntity.noContent().build();
    }

    /** Finished-goods GTIN (AI 01 on the labels). Blank = no GTIN. */
    private void applyGtin(Product prod, String gtin) {
        String g = gtin == null || gtin.isBlank() ? null : gtin.strip();
        if (g != null) products.findByGtin(g).filter(o -> !o.getId().equals(prod.getId()))
                .ifPresent(o -> { throw ApiError.conflict("O GTIN " + g + " já está no produto " + o.getCode() + "."); });
        prod.setGtin(g);
    }
}
