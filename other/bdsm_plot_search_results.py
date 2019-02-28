import matplotlib.pyplot as plt, numpy as np, os

data_file = 'search_compare_data.txt'
if not os.path.exists(data_file):
	exit()
data = np.loadtxt(data_file, delimiter=',', skiprows=1)

# Get columns
astar_path_costs = data[:,1]
gbfs_path_costs = data[:,2]
best_distances = data[:,0]

# First figure, A* best_distance x path_cost
fig = plt.figure(0)
fig.canvas.set_window_title('CS 5013 - BDSM - A* vs GBFS')
# plt.plot(range(len(mean_validation)),mean_validation)

plt.title("min_cost x final_cost")
plt.scatter(best_distances, astar_path_costs)
plt.scatter(best_distances, gbfs_path_costs)
plt.xlabel('min_cost')
plt.ylabel('final_cost')
ax = fig.axes[0]
ax.legend(['A*', 'GBFS'])

plt.show()