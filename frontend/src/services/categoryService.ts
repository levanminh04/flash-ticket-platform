import axiosClient from "../lib/axiosClient";
import { Category } from "../types/api";

export const categoryService = {
  getCategories: async () => {
    const response = await axiosClient.get<{ id: string; name: string }[]>("/api/v1/categories");
    return response.data.map((category, displayOrder) => ({ ...category, slug: category.id, displayOrder }));
  },

  getCategoryBySlug: async (slug: string) => {
    return (await categoryService.getCategories()).find(category => category.id === slug) as Category | undefined;
  },
};
