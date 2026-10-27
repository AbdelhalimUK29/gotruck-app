import type { User } from "@/types/company-space";

export default function UserList({ users }: { users: User[] }) {
  return (
    <div className="w-full rounded-xl border border-border flex flex-col">
      {/* Top bar with count and add button */}
      <div className="border-b border-border p-3 flex items-center justify-between flex-shrink-0">
        <span className="text-sm font-medium text-text-secondary">
          {users.length} user{users.length > 1 ? "s" : ""}
        </span>
        <button className="text-xs text-primary font-medium">+ Add User</button>
      </div>

      {/* Table Row */}
      <div className="flex-1">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-border text-xs text-text-secondary uppercase tracking-wider flex-shrink-0">
              <th className="px-3 py-2 text-left flex-1">User</th>
              <th className="px-3 py-2 text-left flex-1">Email</th>
              <th className="px-3 py-2 text-left flex-1">Role</th>
              <th className="px-3 py-2 text-right">Action</th>
            </tr>
          </thead>
          <tbody>
            {users.map((user) => (
              <tr key={user.id} className="border-b border-border/50 hover:bg-gray-50 flex-shrink-0">
                <td className="px-3 py-2 flex-1">
                  <div className="flex items-center gap-2">
                    <div className="h-6 w-6 rounded-full bg-primary/10 flex items-center justify-center text-xs font-semibold text-primary">
                      {user.email[0].toUpperCase()}
                    </div>
                    <span className="truncate max-w-[100px] text-text-primary">
                      {user.email.split("@")[0]}
                    </span>
                  </div>
                </td>
                <td className="px-3 py-2 text-text-secondary truncate flex-1">
                  {user.email}
                </td>
                <td className="px-3 py-2 flex-1">
                  <span className="rounded-full px-2 py-0.5 text-xs font-medium bg-blue-50 text-blue-700 inline-block whitespace-nowrap">
                    {user.role}
                  </span>
                </td>
                <td className="px-3 py-2 text-right">
                  <button className="text-xs text-text-secondary hover:text-primary whitespace-nowrap">
                    Edit
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>

        {/* Empty state */}
        {users.length === 0 && (
          <div className="h-full flex flex-col items-center justify-center gap-2 p-12">
            <svg
              className="h-12 w-12 text-gray-300"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z"
              />
            </svg>
            <p className="text-sm text-text-secondary">No users yet</p>
            <button className="text-sm text-primary font-medium whitespace-nowrap">
              Add your first user
            </button>
          </div>
        )}
      </div>
    </div>
  );
}