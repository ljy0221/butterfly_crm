import { useQuery } from "@tanstack/react-query";
import * as userApi from "@/api/user";
import type { UserProfile } from "@/api/user";

export const userKeys = {
  profile: (projectId: number, userId: number) =>
    ["users", projectId, "profile", userId] as const,
};

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
