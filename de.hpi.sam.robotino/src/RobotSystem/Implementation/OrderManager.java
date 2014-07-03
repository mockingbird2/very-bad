package RobotSystem.Implementation;

import java.util.Date;
import java.util.List;

import Datatypes.Added.RoomPoint;
import Datatypes.Added.Route;
import RobotSystem.Interfaces.New.IRobotExecute;
import de.hpi.sam.warehouse.WarehouseRobot;
import de.hpi.sam.warehouse.environment.IWarehouseEnvironment;
import de.hpi.sam.warehouse.order.Order;
import de.hpi.sam.warehouse.order.OrderItem;
import de.hpi.sam.warehouse.stock.Cart;
import de.hpi.sam.warehouse.stock.IssuingPoint;
import de.hpi.sam.warehouse.stock.StockroomID;
import de.hpi.sam.warehouse.stock.WarehouseRepresentation;
import RobotSystem.Implementation.*;

public class OrderManager {
// DriverManager, ExplorationManager

	RouteFinder rf;
	DriveManager dm;
	
	private boolean done;
	private Route currentSourceRoute = null;
	private Route currentDestinationRoute = null;
	private Order currentOrder = null;
	private Cart currentCart = null;
	private WarehouseRobot robot = null;
	private WarehouseRepresentation rep = null;
	private ExplorationManager em;
	
	public OrderManager(WarehouseRobot r, WarehouseRepresentation wr) {
		robot = r;
		rep = wr;
		dm = new DriveManager(robot);
		rf = new RouteFinder(robot, rep);
		em = new ExplorationManager(r, this.rep);
	}
	/*
	private Route chooseOrderRoute(List<Route> routes, Order order) 	
		return null;
	}
	*/
	
	private Route chooseRoute(List<Route> routes) {
		Route min = routes.get(0);
		for (Route route : routes) {
			if (route.getDistance() < min.getDistance())
				min = route;
		}
		return min;
	}

	public void orderStart() {
		System.out.println("starting order");
		// No order nothing to do //TODO an error message might be good
		if(currentOrder == null)
			return;
		done = false;
		// Getting to the cart area and the position of the first cart
		robot.driveToPositionAvoidingObstacles(currentOrder.getCartArea().getCartPositions().get(0));
		if(dm.isBumped()) {
			System.out.println("Getting to cart area failed");
			return;
		}
		
		// Get the cart
		currentCart = robot.takeCart(currentOrder.getCartArea().getCartPositions().get(0));
		if(currentCart == null) {
			System.out.println("Error retrieving cart for CartArea");
			return;
		}
		
		for (OrderItem orderItem : currentOrder.getOrderItems()) {
			System.out.println("processing orderitem");
			if( robot.getIssuingPoints(orderItem.getProductType()).size() < 1) {
				System.out.println("Error: No issuings points for order item");
				return;
			}
			// Find nearest issuing points 
			
			int robotX, robotZ;
			robotX = robot.getCurrentPosition().getXPosition();
			robotZ = robot.getCurrentPosition().getZPosition();
			IssuingPoint nearest = robot.getIssuingPoints(orderItem.getProductType()).get(0);
			for(IssuingPoint point : robot.getIssuingPoints(orderItem.getProductType())) {
				if(point.getXPosition() - robotX + point.getZPosition() - robotZ  < nearest.getXPosition() - robotX + nearest.getZPosition() - robotZ) {
					nearest = point;
				}
			}
			
			// Drive to issuingpoint and retrieve items
			robot.driveToPositionAvoidingObstacles(nearest);
			robot.load(orderItem.getQuantity(), nearest, currentCart);
		}
		System.out.println("finished order");
		// TODO for testing we return to the current cart area
		robot.driveToPositionAvoidingObstacles(currentOrder.getCartArea().getCartPositions().get(0));
		currentCart = null;	
		done = true;
	}

	public boolean orderDone() {
		return done;
	}

	public Date calculateOrderTime(Order order) {
		int distance = 0;
		int explorationDistance = 0;
		List<Route> cartAreaRoutes = rf.calculateCartAreaRoutes(rf.getPosition());
		List<Route> issuingPointRoutes = rf.calculateIssuingPointsRoutes(rf.getPosition(), order);
		List<Route> finalCartAreaRoutes = rf.calculateCartAreaRoutes(rf.getPosition(), order);
		
		// PLACEHOLDER
		Route shortestCartAreaRoute = chooseRoute(cartAreaRoutes);
		Route shortestIssuingPointRoutes = chooseRoute(issuingPointRoutes);
		Route shortestCartAreaRoutes = chooseRoute(finalCartAreaRoutes);
		
		// calculate exploring distance if unexplored rooms exist
		for (int i = 0; i < 3; i++) {
			List<RoomPoint> rp;
			
			// calculate distance for each subroute
			Route route;
			switch (i) {
				case (0):
					route = shortestCartAreaRoute;
					rp = route.getRoomPoints();
					break;
				case (1):
					route = shortestIssuingPointRoutes;
					rp = route.getRoomPoints();
					break;
				default:
					route = shortestCartAreaRoutes;
					rp = route.getRoomPoints();
					break;
			}
			// check if the room is explored
			for (RoomPoint r : rp) {
				if (!em.isExplored(r.getRoom())) {
					Route explorationRoute = rf.calculateExplorationRoute(rf.getPosition(), r.getRoom());
					explorationDistance += rf.getDistance(explorationRoute);
				}
				else {
					distance += rf.getDistance(route);
				}
			}
		}
		
		// time in milli seconds
		int explorationTime = explorationDistance/IWarehouseEnvironment.SAFETY_SPEED * 1000;
		int routeTime = distance/dm.getMaxSpeed() * 1000;
		Date date = new Date(explorationTime + routeTime);
		
		return date;
	}

	

}