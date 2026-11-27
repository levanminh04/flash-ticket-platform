import axiosClient from "../lib/axiosClient";
import { Category } from "../types/api";

export const categoryService = {
  getCategories: async (signal?: AbortSignal) => {
    const response = await axiosClient.get<{ id: string; name: string }[]>("/api/v1/categories", { signal });
    return response.data.map((category, displayOrder) => ({ ...category, slug: category.id, displayOrder }));
  },

  getCategoryBySlug: async (slug: string, signal?: AbortSignal) => {
    return (await categoryService.getCategories(signal)).find(category => category.id === slug) as Category | undefined;
  },
};
