import matplotlib.pyplot as plt, numpy as np, os

gbfs_file = 'gbfs_data.txt'
if not os.path.exists(gbfs_file):
	exit()
gbfs_data = np.loadtxt(gbfs_file, delimiter=',', skiprows=1)
astar_file = 'astar_data.txt'
if not os.path.exists(astar_file):
	exit()
astar_data = np.loadtxt(astar_file, delimiter=',', skiprows=1)

# Get columns
astar_path_costs = astar_data[:,2]
astar_plan_time = astar_data[:,0]
astar_best_distances = astar_data[:,3]
astar_tree_sizes = astar_data[:,1]
gbfs_path_costs = gbfs_data[:,2]
gbfs_plan_time = gbfs_data[:,0]
gbfs_best_distances = gbfs_data[:,3]
gbfs_tree_sizes = gbfs_data[:,1]

# First figure, A* best_distance x path_cost
fig = plt.figure(0)
fig.canvas.set_window_title('CS 5013 - BDSM - A* vs GBFS')
# plt.plot(range(len(mean_validation)),mean_validation)

plt.title("min_cost x final_cost")
plt.scatter(astar_best_distances, astar_path_costs)
plt.scatter(gbfs_best_distances, gbfs_path_costs)
plt.xlabel('min_cost')
plt.ylabel('final_cost')
ax = fig.axes[0]
ax.legend(['A*', 'GBFS'])

fig = plt.figure(1)
fig.canvas.set_window_title('CS 5013 - BDSM - A* vs GBFS')
plt.scatter(astar_best_distances, astar_plan_time)
plt.scatter(gbfs_best_distances, gbfs_plan_time)
plt.xlabel('min_cost')
plt.ylabel('cpu_time')
plt.title("min_cost x cpu_time")
ax = fig.axes[0]
ax.legend(['A*', 'GBFS'])

fig = plt.figure(2)
fig.canvas.set_window_title('CS 5013 - BDSM - A* vs GBFS')
plt.scatter(astar_best_distances, astar_tree_sizes)
plt.scatter(gbfs_best_distances, gbfs_tree_sizes)
plt.xlabel('min_cost')
plt.ylabel('tree_size')
plt.title("min_cost x tree_size")
ax = fig.axes[0]
ax.legend(['A*', 'GBFS'])
plt.show()