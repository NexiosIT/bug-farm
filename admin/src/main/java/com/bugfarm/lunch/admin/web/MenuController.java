package com.bugfarm.lunch.admin.web;

import com.bugfarm.lunch.admin.menu.FoodTypeView;
import com.bugfarm.lunch.admin.menu.ItemView;
import com.bugfarm.lunch.admin.menu.MenuRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/menu")
public class MenuController {

    public record NameRequest(String name) {}

    private final MenuRepository menu;

    public MenuController(MenuRepository menu) {
        this.menu = menu;
    }

    @GetMapping
    public List<FoodTypeView> all() {
        return menu.all();
    }

    @PostMapping("/food-types")
    @ResponseStatus(HttpStatus.CREATED)
    public FoodTypeView createFoodType(@RequestBody NameRequest request) {
        return menu.createFoodType(request.name());
    }

    @PutMapping("/food-types/{id}")
    public FoodTypeView renameFoodType(@PathVariable long id, @RequestBody NameRequest request) {
        return menu.renameFoodType(id, request.name());
    }

    @DeleteMapping("/food-types/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFoodType(@PathVariable long id) {
        menu.deleteFoodType(id);
    }

    @PostMapping("/food-types/{id}/items")
    @ResponseStatus(HttpStatus.CREATED)
    public ItemView addItem(@PathVariable long id, @RequestBody NameRequest request) {
        return menu.addItem(id, request.name());
    }

    @PutMapping("/items/{id}")
    public ItemView renameItem(@PathVariable long id, @RequestBody NameRequest request) {
        return menu.renameItem(id, request.name());
    }

    @DeleteMapping("/items/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteItem(@PathVariable long id) {
        menu.deleteItem(id);
    }
}
