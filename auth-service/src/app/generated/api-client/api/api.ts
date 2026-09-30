export * from './accountController.service';
import { AccountControllerService } from './accountController.service';
export * from './orderController.service';
import { OrderControllerService } from './orderController.service';
export const APIS = [AccountControllerService, OrderControllerService];
