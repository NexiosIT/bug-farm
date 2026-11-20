package com.bugfarm.lunch.admin.menu;

import java.util.List;

public record FoodTypeView(long id, String name, List<ItemView> items) {}
