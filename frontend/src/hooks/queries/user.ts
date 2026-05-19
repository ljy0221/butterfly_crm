import { useQuery } from "@tanstack/react-query";
import * as userApi from "@/api/user";
import type { PageResponse } from "@/api/types";
import type { UserListItem, UserProfile } from "@/api/user";

export const userKeys = {
  list: (projectId: number, page: number, size: number, search: string) =>
    ["users", projectId, "list", page, size, search] as const,
  profile: (projectId: number, userId: number) =>
    ["users", projectId, "profile", userId] as const,
};

export function useUsersQuery(
  projectId: number | null,
  params: userApi.ListUsersParams = {},
) {
  const page = params.page ?? 0;
  const size = params.size ?? 20;
  const search = params.search ?? "";

  return useQuery<PageResponse<UserListItem>>({
    queryKey: userKeys.list(projectId ?? -1, page, size, search),
    queryFn: () =>
      userApi.listUsers(projectId as number, {
        page,
        size,
        search,
        sort: params.sort ?? "createdAt,desc",
      }),
    enabled:
      projectId != null &&
      Number.isFinite(projectId),
    staleTime: 30_000,
  });
}

export function useUserProfileQuery(
  projectId: number | null,
  userId: number | null,
  enabled: boolean = true,
) {
  return useQuery<UserProfile>({
    queryKey: userKeys.profile(projectId ?? -1, userId ?? -1),
    queryFn: () =>
      userApi.getUserProfile(projectId as number, userId as number),
    enabled:
      enabled &&
      projectId != null &&
      Number.isFinite(projectId) &&
      userId != null &&
      Number.isFinite(userId),
    staleTime: 30_000,
  });
}
